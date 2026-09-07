package com.example.outbox.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateOrderRequest", description = "Order intake payload")
public record CreateOrderRequest(
        @Schema(description = "Customer identifier", example = "123", maxLength = 64)
        @NotBlank
        @Size(max = 64)
        String customerId) {

    public CreateOrderRequest {
        if (customerId != null) {
            customerId = customerId.trim();
        }
    }
}
