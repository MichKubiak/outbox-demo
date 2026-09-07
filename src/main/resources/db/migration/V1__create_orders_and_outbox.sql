create table orders (
    id          uuid primary key,
    customer_id varchar(64)  not null,
    status      varchar(32)  not null,
    created_at  timestamptz  not null
);

create table outbox_events (
    id             uuid         primary key,
    aggregate_type varchar(64)  not null,
    aggregate_id   uuid         not null,
    event_type     varchar(64)  not null,
    payload        jsonb        not null,
    created_at     timestamptz  not null,
    processed_at   timestamptz,
    attempts       integer      not null default 0
);

create index idx_outbox_unprocessed
    on outbox_events (created_at)
    where processed_at is null;

create index idx_outbox_aggregate
    on outbox_events (aggregate_type, aggregate_id);
