# outbox

Transactional outbox for order intake, on Spring Boot 3.5 and PostgreSQL 16.

## What it does

`POST /orders` writes two rows in one transaction:

1. the order into `orders`
2. an `OrderCreated` event into `outbox_events`, payload as `jsonb`

Both writes happen inside `OrderService.createOrder`, so an event can never be persisted without
its order and vice versa. `OutboxPublisherScheduler` then polls every 5 seconds, and for each
unprocessed row `OutboxPublisher.publishBatch` logs the event at INFO and stamps `processed_at`
in the same transaction. A failed tick commits nothing, so the rows stay unprocessed and are
retried on the next tick.

Log line produced by the publisher:

```
Outbox event published: id=<uuid> type=OrderCreated aggregateType=Order aggregateId=<orderId> payload={...}
```

Layout is layered: `controller` -> `service` -> `repository` -> `entity`, with `config`, `web`
and `health` alongside. `ArchitectureTest` enforces the layer ordering with ArchUnit, so a
repository call from a controller fails the build. Schema is owned by Flyway
(`V1__create_orders_and_outbox.sql`); Hibernate runs with `ddl-auto: validate` and never
alters it.

## Prerequisites

- JDK 17 or newer. The build targets `release 17` regardless of the JDK you run it with.
- Docker and Compose v2. Required to run the stack, and required for the integration tests,
  which start PostgreSQL through Testcontainers.
- No local Gradle install. Use the wrapper, which pins Gradle 8.14.5.

## Build

```bash
./gradlew build
```

Compiles, runs the full test suite, writes the JaCoCo report, and produces
`build/libs/outbox-1.0.0.jar`. Needs Docker, because `build` includes the integration tests.

Jar only, no tests:

```bash
./gradlew bootJar
```

## Run

The stack:

```bash
docker compose up --build
```

Two services. `postgres` is `postgres:16-alpine` with a `pg_isready` healthcheck; `app` waits
for it to report healthy, then Flyway applies the migration on startup. The application listens
on `http://localhost:8080`. The image is multi-stage, runs as the non-root `outbox` user, and
carries a `HEALTHCHECK` against `/actuator/health/readiness`.

Wait for readiness instead of tailing logs:

```bash
docker compose up --build --detach --wait --wait-timeout 240
```


## API

Swagger UI at `http://localhost:8080/swagger-ui.html`, OpenAPI document at `/v3/api-docs`.
The `servers` entry is derived from the request, so "Try it out" also works when the stack runs
on a non-default `APP_PORT`. Full reference, including every error case, in `docs/endpoints.md`.

| Method | Path | Success |
| --- | --- | --- |
| POST | `/orders` | 201 |
| GET | `/v3/api-docs`, `/swagger-ui.html` | 200, 302 |

Create an order:

```bash
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"123"}'
```

```json
{
  "orderId": "e2332902-69bd-4d24-8aca-60f61bee6064",
  "customerId": "123",
  "status": "CREATED",
  "createdAt": "2026-09-07T01:46:26.349731Z"
}
```

`customerId` is required, trimmed on binding, and capped at 64 characters. Failures come back
through `GlobalExceptionHandler` as a fixed envelope with a stable `code` from `ErrorCode`, never
a stack trace:

```bash
curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"  "}'
```

```json
{
  "timestamp": "2026-09-07T01:46:48.610436Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "customerId: must not be blank",
  "path": "/orders",
  "correlationId": "2e5743da-fd31-4407-a14c-88948e2eaeb8"
}
```

Every response carries `X-Correlation-Id`, taken from the request header when it matches
`[A-Za-z0-9-]{1,64}` and generated otherwise, plus the headers set by `SecurityHeadersFilter`.
`/orders` is rate limited to 100 requests per minute per client IP through Bucket4j; responses
carry `X-RateLimit-Remaining`, and a rejection is a `429` with `Retry-After`. Actuator and
OpenAPI paths are not rate limited. Nothing is authenticated.

Business metrics, tagged `application=outbox` and `environment=${APP_ENVIRONMENT}`:

| Metric | On `/actuator/prometheus` | Meaning |
| --- | --- | --- |
| `orders.created` | `orders_total` | orders committed with their outbox event |
| `outbox.events.published` | `outbox_events_published_total` | events logged and marked processed |
| `outbox.publish.duration` | `outbox_publish_duration_seconds` | duration of one publisher tick |
| `outbox.backlog.size` | `outbox_backlog_size` | unprocessed rows |

`/actuator/health` also reports `outboxBacklog`, which goes DOWN when the oldest unprocessed
event is older than `outbox.publisher.backlog-warn-age-seconds` (60 by default). It is not part
of the readiness group, so a stalled publisher never takes the instance out of rotation.

## API Examples

Every command in this section was run against the Compose stack and returned the response shown.
`docker-compose.yml` publishes `${APP_PORT:-8080}`, so point `BASE` at the port the stack was
started on:

```bash
docker compose up --detach --build --wait
BASE=http://localhost:8080
```

### Create an order

```bash
curl -s -X POST $BASE/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"123"}'
# Response: {"orderId":"02f95fb4-0449-48b6-aec4-0bb9f11fa2b8","customerId":"123","status":"CREATED","createdAt":"2026-09-07T04:04:45.736499846Z"}
```

`customerId` is trimmed on binding, so `"   padded-42   "` is stored as `padded-42`. A
64-character value is accepted with `201`, a 65-character value is rejected with `400`. Non-ASCII
values, including CJK, accented Latin and emoji code points, round-trip unchanged through the
API, the `orders` row and the outbox payload.

### Correlation id and response headers

```bash
curl -s -D - -o /dev/null -X POST $BASE/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: local-check-1' \
  -d '{"customerId":"123"}'
```

```
HTTP/1.1 201
X-Correlation-Id: local-check-1
X-RateLimit-Remaining: 91
Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; ...
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
Referrer-Policy: no-referrer
```

A supplied id is echoed back when it matches `[A-Za-z0-9-]{1,64}`; otherwise a UUID is generated.

### Error responses

```bash
curl -s -X POST $BASE/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"  "}'
# Response: {"timestamp":"2026-09-07T04:04:56.466534928Z","status":400,"code":"VALIDATION_FAILED","message":"customerId: must not be blank","path":"/orders","correlationId":"70448721-27f2-453d-996b-ba5eeafc84f7"}
```