package com.example.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.qos.logback.classic.Level;
import com.example.outbox.config.OutboxProperties;
import com.example.outbox.entity.OutboxEvent;
import com.example.outbox.repository.OutboxEventRepository;
import com.example.outbox.support.LogCapture;
import com.example.outbox.support.TestEntities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-07T10:15:30Z");
    private static final int BATCH_SIZE = 25;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OutboxEventRepository repository;

    private SimpleMeterRegistry meterRegistry;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(true, 5000L, BATCH_SIZE, 60L);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new OutboxPublisher(repository, properties, clock, meterRegistry);
    }

    @Test
    void should_returnZero_when_batchIsEmpty() {
        // given
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of());

        // when
        int published = publisher.publishBatch();

        // then
        assertThat(published).isZero();
    }

    @Test
    void should_notLog_when_batchIsEmpty() {
        // given
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of());

        // when
        try (LogCapture logs = LogCapture.attachTo(OutboxPublisher.class)) {
            publisher.publishBatch();

            // then
            assertThat(logs.messagesAt(Level.INFO)).isEmpty();
        }
    }

    @Test
    void should_requestConfiguredBatchSize_when_polling() {
        // given
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of());

        // when
        publisher.publishBatch();

        // then
        then(repository).should().findUnprocessedBatch(BATCH_SIZE);
    }

    @Test
    void should_logAndMarkProcessed_when_unprocessedEventsExist() {
        // given
        OutboxEvent event = event();
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of(event));

        // when
        try (LogCapture logs = LogCapture.attachTo(OutboxPublisher.class)) {
            publisher.publishBatch();

            // then
            assertThat(logs.messagesAt(Level.INFO))
                    .hasSize(1)
                    .allSatisfy(message -> assertThat(message).contains("OrderCreated", event.getId().toString()));
        }
        assertThat(event.getProcessedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_incrementAttempts_when_eventProcessed() {
        // given
        OutboxEvent event = event();
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of(event));

        // when
        publisher.publishBatch();

        // then
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void should_returnPublishedCount_when_batchProcessed() {
        // given
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of(event(), event(), event()));

        // when
        int published = publisher.publishBatch();

        // then
        assertThat(published).isEqualTo(3);
    }

    @Test
    void should_propagateException_when_repositoryFails() {
        // given
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willThrow(new QueryTimeoutException("database down"));

        // when
        // then
        assertThatThrownBy(() -> publisher.publishBatch()).isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void should_publishBacklogSize_when_gaugeRefreshed() {
        // given
        given(repository.countByProcessedAtIsNull()).willReturn(7L);

        // when
        publisher.refreshBacklogGauge();

        // then
        assertThat(publisher.currentBacklogGauge()).isEqualTo(7L);
    }

    @Test
    void should_exposeBacklogGaugeInRegistry_when_gaugeRefreshed() {
        // given
        given(repository.countByProcessedAtIsNull()).willReturn(7L);

        // when
        publisher.refreshBacklogGauge();

        // then
        assertThat(meterRegistry.get("outbox.backlog.size").gauge().value()).isEqualTo(7.0);
    }

    @Test
    void should_publishBacklogSize_when_applicationBecomesReady() {
        // given
        given(repository.countByProcessedAtIsNull()).willReturn(3L);

        // when
        publisher.primeBacklogGauge();

        // then
        assertThat(publisher.currentBacklogGauge()).isEqualTo(3L);
    }

    @Test
    void should_reportEmptyBacklog_when_noRowsAreUnprocessed() {
        // given
        given(repository.countByProcessedAtIsNull()).willReturn(0L);

        // when
        publisher.refreshBacklogGauge();

        // then
        assertThat(publisher.currentBacklogGauge()).isZero();
    }

    @Test
    void should_markEveryEventWithSameTimestamp_when_batchIsProcessed() {
        // given
        OutboxEvent first = event();
        OutboxEvent second = event();
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of(first, second));

        // when
        publisher.publishBatch();

        // then
        assertThat(first.getProcessedAt()).isEqualTo(second.getProcessedAt());
    }

    @Test
    void should_logEveryEvent_when_batchHoldsMultipleEvents() {
        // given
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of(event(), event(), event()));

        // when
        try (LogCapture logs = LogCapture.attachTo(OutboxPublisher.class)) {
            publisher.publishBatch();

            // then
            assertThat(logs.messagesAt(Level.INFO)).hasSize(3);
        }
    }

    @Test
    void should_logPayload_when_eventIsPublished() {
        // given
        OutboxEvent event = TestEntities.event(UUID.randomUUID(), UUID.randomUUID(),
                "{\"customerId\":\"123\"}", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        given(repository.findUnprocessedBatch(BATCH_SIZE)).willReturn(List.of(event));

        // when
        try (LogCapture logs = LogCapture.attachTo(OutboxPublisher.class)) {
            publisher.publishBatch();

            // then
            assertThat(logs.messagesAt(Level.INFO).get(0))
                    .contains("{\"customerId\":\"123\"}", event.getAggregateId().toString());
        }
    }

    private OutboxEvent event() {
        return TestEntities.event(UUID.randomUUID(), UUID.randomUUID(), "{}",
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }
}
