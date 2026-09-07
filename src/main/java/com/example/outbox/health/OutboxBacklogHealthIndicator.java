package com.example.outbox.health;

import com.example.outbox.config.OutboxProperties;
import com.example.outbox.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OutboxBacklogHealthIndicator implements HealthIndicator {

    private final OutboxEventRepository repository;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxBacklogHealthIndicator(OutboxEventRepository repository, OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Health health() {
        Optional<Instant> oldest = repository.findOldestUnprocessedCreatedAt();
        long warnAgeSeconds = properties.backlogWarnAgeSeconds();
        if (oldest.isEmpty()) {
            return Health.up()
                    .withDetail("oldestUnprocessedAgeSeconds", 0L)
                    .withDetail("warnAgeSeconds", warnAgeSeconds)
                    .build();
        }
        long ageSeconds = Duration.between(oldest.get(), clock.instant()).getSeconds();
        Health.Builder builder = ageSeconds > warnAgeSeconds ? Health.down() : Health.up();
        return builder
                .withDetail("oldestUnprocessedAgeSeconds", ageSeconds)
                .withDetail("warnAgeSeconds", warnAgeSeconds)
                .build();
    }
}
