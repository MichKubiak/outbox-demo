package com.example.outbox.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.outbox.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ActuatorHealthIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long STALE_MINUTES = 10;

    @Test
    void should_reportUp_when_dependenciesAreReachable() throws Exception {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(response.getBody()).get("status").asText()).isEqualTo("UP");
    }

    @Test
    void should_exposeCustomIndicators_when_healthQueried() throws Exception {
        // given
        // when
        JsonNode components = healthBody("/actuator/health").get("components");

        // then
        assertThat(components.fieldNames()).toIterable().contains("database", "outboxBacklog", "db");
    }

    @Test
    void should_reportDatabaseProduct_when_healthQueried() throws Exception {
        // given
        // when
        JsonNode details = healthBody("/actuator/health").at("/components/database/details");

        // then
        assertThat(details.get("product").asText()).isEqualTo("PostgreSQL");
        assertThat(details.get("version").asText()).isNotBlank();
    }

    @Test
    void should_reportEmptyBacklog_when_noEventsPending() throws Exception {
        // given
        // when
        JsonNode backlog = healthBody("/actuator/health").at("/components/outboxBacklog");

        // then
        assertThat(backlog.get("status").asText()).isEqualTo("UP");
        assertThat(backlog.at("/details/oldestUnprocessedAgeSeconds").asLong()).isZero();
        assertThat(backlog.at("/details/warnAgeSeconds").asLong()).isEqualTo(60L);
    }

    @Test
    void should_reportUp_when_backlogIsWithinWarnAge() throws Exception {
        // given
        insertUnprocessedEvent(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(5));

        // when
        JsonNode backlog = healthBody("/actuator/health").at("/components/outboxBacklog");

        // then
        assertThat(backlog.get("status").asText()).isEqualTo("UP");
    }

    @Test
    void should_reportDown_when_backlogIsStale() throws Exception {
        // given
        insertUnprocessedEvent(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(STALE_MINUTES));

        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.get("status").asText()).isEqualTo("DOWN");
        assertThat(body.at("/components/outboxBacklog/status").asText()).isEqualTo("DOWN");
        assertThat(body.at("/components/outboxBacklog/details/oldestUnprocessedAgeSeconds").asLong())
                .isGreaterThanOrEqualTo(STALE_MINUTES * 60);
    }

    @Test
    void should_stayUp_when_livenessProbeQueriedWithStaleBacklog() throws Exception {
        // given
        insertUnprocessedEvent(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(STALE_MINUTES));

        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health/liveness", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(response.getBody()).get("status").asText()).isEqualTo("UP");
    }

    @Test
    void should_ignoreDependencies_when_livenessProbeQueried() throws Exception {
        // given
        // when
        JsonNode body = healthBody("/actuator/health/liveness");

        // then
        assertThat(body.get("status").asText()).isEqualTo("UP");
        assertThat(body.toString()).doesNotContain("outboxBacklog", "database", "diskSpace");
    }

    @Test
    void should_includeDatabase_when_readinessProbeQueried() throws Exception {
        // given
        // when
        JsonNode body = healthBody("/actuator/health/readiness");

        // then
        assertThat(body.get("status").asText()).isEqualTo("UP");
        assertThat(body.get("components").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("db", "readinessState");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"/actuator/beans", "/actuator/env", "/actuator/loggers", "/actuator/configprops"})
    void should_returnNotFound_when_endpointIsNotExposed(String path) {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode healthBody(String path) throws Exception {
        return JSON.readTree(restTemplate.getForEntity(path, String.class).getBody());
    }

    private void insertUnprocessedEvent(OffsetDateTime createdAt) {
        jdbcTemplate.update("""
                insert into outbox_events
                    (id, aggregate_type, aggregate_id, event_type, payload, created_at, processed_at, attempts)
                values (?, 'Order', ?, 'OrderCreated', cast(? as jsonb), ?, null, 0)
                """, UUID.randomUUID(), UUID.randomUUID(), "{\"orderId\":\"" + UUID.randomUUID() + "\"}", createdAt);
    }
}
