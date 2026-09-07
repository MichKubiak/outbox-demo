package com.example.outbox.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
        @DefaultValue("100") int capacity,
        @DefaultValue("PT1M") Duration refillPeriod,
        @DefaultValue List<String> trustedProxies,
        @DefaultValue("100000") int maxTrackedClients,
        @DefaultValue("PT5M") Duration sweepInterval) {

    public RateLimitProperties {
        if (capacity < 1) {
            throw new IllegalArgumentException("ratelimit.capacity must be positive");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("ratelimit.refill-period must be positive");
        }
        if (maxTrackedClients < 1) {
            throw new IllegalArgumentException("ratelimit.max-tracked-clients must be positive");
        }
        if (sweepInterval == null || sweepInterval.isZero() || sweepInterval.isNegative()) {
            throw new IllegalArgumentException("ratelimit.sweep-interval must be positive");
        }
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
