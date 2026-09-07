package com.example.outbox.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.outbox.entity.OrderStatus;
import com.example.outbox.service.CreatedOrder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderResponseTest {

    @Test
    void should_mapAllFields_when_createdOrderGiven() {
        // given
        UUID orderId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 9, 7, 10, 15, 30, 0, ZoneOffset.UTC);
        CreatedOrder created = new CreatedOrder(orderId, "123", OrderStatus.CREATED, createdAt);

        // when
        OrderResponse response = OrderResponse.from(created);

        // then
        assertThat(response).isEqualTo(new OrderResponse(orderId, "123", "CREATED", createdAt));
    }
}
