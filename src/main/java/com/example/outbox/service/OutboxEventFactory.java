package com.example.outbox.service;

import com.example.outbox.entity.OrderEntity;
import com.example.outbox.entity.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventFactory {

    static final String AGGREGATE_TYPE = "Order";
    static final String EVENT_TYPE = "OrderCreated";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventFactory(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public OutboxEvent orderCreated(OrderEntity order) {
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);
        OrderCreatedPayload payload = new OrderCreatedPayload(
                order.getId(), order.getCustomerId(), order.getStatus().name(), occurredAt);
        return OutboxEvent.create(AGGREGATE_TYPE, order.getId(), EVENT_TYPE, serialize(payload), occurredAt);
    }

    private String serialize(OrderCreatedPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize OrderCreated payload", e);
        }
    }

    public record OrderCreatedPayload(UUID orderId, String customerId, String status, OffsetDateTime occurredAt) {
    }
}
