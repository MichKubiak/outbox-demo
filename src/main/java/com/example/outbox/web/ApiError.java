package com.example.outbox.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "ApiError", description = "Error response body")
public record ApiError(
        @Schema(description = "Time the error was produced") OffsetDateTime timestamp,
        @Schema(description = "HTTP status code", example = "400") int status,
        @Schema(description = "Stable client-facing error code") ErrorCode code,
        @Schema(description = "Human readable summary, never a stack trace") String message,
        @Schema(description = "Request path", example = "/orders") String path,
        @Schema(description = "Correlation identifier echoed in the response header") String correlationId) {
}
