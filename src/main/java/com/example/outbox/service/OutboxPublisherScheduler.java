package com.example.outbox.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {

    private final OutboxPublisher publisher;
    private final Counter publishedCounter;
    private final Timer publishTimer;

    public OutboxPublisherScheduler(OutboxPublisher publisher, MeterRegistry meterRegistry) {
        this.publisher = publisher;
        this.publishedCounter = Counter.builder("outbox.events.published")
                .description("Outbox events logged and marked processed")
                .register(meterRegistry);
        this.publishTimer = Timer.builder("outbox.publish.duration")
                .description("Duration of a committed outbox publish batch")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    public void tick() {
        long startedAt = System.nanoTime();
        try {
            int published = publisher.publishBatch();
            publishTimer.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            if (published > 0) {
                publishedCounter.increment(published);
            }
            publisher.refreshBacklogGauge();
        } catch (Exception e) {
            log.error("Outbox publish tick failed, rows stay unprocessed and are retried", e);
        }
    }
}
