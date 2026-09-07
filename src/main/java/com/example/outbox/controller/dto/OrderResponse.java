package com.example.outbox.controller.dto;

import com.example.outbox.service.CreatedOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "OrderResponse", description = "Created order")
public record OrderResponse(
        @Schema(description = "Generated order identifier") UUID orderId,
        @Schema(description = "Customer identifier", example = "123") String customerId,
        @Schema(description = "Order status", example = "CREATED") String status,
        @Schema(description = "Creation timestamp in UTC") OffsetDateTime createdAt) {

    public static OrderResponse from(CreatedOrder order) {
        return new OrderResponse(order.orderId(), order.customerId(), order.status().name(), order.createdAt());
    }
}
