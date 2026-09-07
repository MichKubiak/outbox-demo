package com.example.outbox.service;

import com.example.outbox.entity.OrderStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreatedOrder(UUID orderId, String customerId, OrderStatus status, OffsetDateTime createdAt) {
}
