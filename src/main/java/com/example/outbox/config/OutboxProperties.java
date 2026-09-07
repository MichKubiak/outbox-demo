package com.example.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "outbox.publisher")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5000") long fixedDelayMs,
        @DefaultValue("100") int batchSize,
        @DefaultValue("60") long backlogWarnAgeSeconds) {

    public OutboxProperties {
        if (fixedDelayMs < 1) {
            throw new IllegalArgumentException("outbox.publisher.fixed-delay-ms must be positive");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("outbox.publisher.batch-size must be positive");
        }
        if (backlogWarnAgeSeconds < 0) {
            throw new IllegalArgumentException("outbox.publisher.backlog-warn-age-seconds must not be negative");
        }
    }
}
