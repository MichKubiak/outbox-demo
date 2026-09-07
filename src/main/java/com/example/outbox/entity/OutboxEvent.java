package com.example.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
@Getter
public class OutboxEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    protected OutboxEvent() {
    }

    public static OutboxEvent create(String aggregateType, UUID aggregateId, String eventType, String payload,
                                     OffsetDateTime createdAt) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        event.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        event.eventType = Objects.requireNonNull(eventType, "eventType");
        event.payload = Objects.requireNonNull(payload, "payload");
        event.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        event.attempts = 0;
        return event;
    }

    public void markProcessed(OffsetDateTime processedAt) {
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
        this.attempts++;
    }

    public boolean isProcessed() {
        return processedAt != null;
    }
}
