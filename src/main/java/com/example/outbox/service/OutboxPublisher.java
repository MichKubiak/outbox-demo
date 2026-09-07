package com.example.outbox.service;

import com.example.outbox.config.OutboxProperties;
import com.example.outbox.entity.OutboxEvent;
import com.example.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final OutboxProperties properties;
    private final Clock clock;
    private final AtomicLong backlogSize;

    public OutboxPublisher(OutboxEventRepository repository, OutboxProperties properties, Clock clock,
                           MeterRegistry meterRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.backlogSize = meterRegistry.gauge("outbox.backlog.size", new AtomicLong());
    }

    @Transactional
    public int publishBatch() {
        List<OutboxEvent> batch = repository.findUnprocessedBatch(properties.batchSize());
        if (batch.isEmpty()) {
            return 0;
        }
        OffsetDateTime processedAt = OffsetDateTime.now(clock);
        for (OutboxEvent event : batch) {
            log.info("Outbox event published: id={} type={} aggregateType={} aggregateId={} payload={}",
                    event.getId(), event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                    event.getPayload());
            event.markProcessed(processedAt);
        }
        return batch.size();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void primeBacklogGauge() {
        refreshBacklogGauge();
    }

    public void refreshBacklogGauge() {
        backlogSize.set(repository.countByProcessedAtIsNull());
    }

    public long currentBacklogGauge() {
        return backlogSize.get();
    }
}
