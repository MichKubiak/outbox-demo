package com.example.outbox.support;

import com.example.outbox.entity.OrderEntity;
import com.example.outbox.entity.OutboxEvent;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestEntities {

    public static final String AGGREGATE_TYPE = "Order";
    public static final String EVENT_TYPE = "OrderCreated";

    private TestEntities() {
    }

    public static OrderEntity order(UUID id, String customerId, OffsetDateTime createdAt) {
        OrderEntity order = OrderEntity.create(customerId, createdAt);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    public static OutboxEvent event(UUID id, UUID aggregateId, String payload, OffsetDateTime createdAt) {
        OutboxEvent event = OutboxEvent.create(AGGREGATE_TYPE, aggregateId, EVENT_TYPE, payload, createdAt);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
