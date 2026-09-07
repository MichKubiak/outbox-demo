package com.example.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.example.outbox.AbstractPostgresIntegrationTest;
import com.example.outbox.entity.OutboxEvent;
import com.example.outbox.repository.OutboxEventRepository;
import com.example.outbox.support.LogCapture;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "outbox.publisher.batch-size=2")
class OutboxPublisherIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final int BATCH_SIZE = 2;
    private static final OffsetDateTime BASE_TIME =
            OffsetDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void should_beAutowirable_when_schedulerIsDisabled() {
        // given
        // when
        // then
        assertThat(publisher).isNotNull();
        assertThat(applicationContext.getBeanNamesForType(OutboxPublisherScheduler.class)).isEmpty();
    }

    @Test
    void should_markEventProcessed_when_publisherRuns() {
        // given
        UUID eventId = insertPendingEvent(BASE_TIME);

        // when
        int published = publisher.publishBatch();

        // then
        assertThat(published).isEqualTo(1);
        assertThat(processedIds()).containsExactly(eventId);
        assertThat(attemptsOf(eventId)).isEqualTo(1);
    }

    @Test
    void should_logEventAtInfo_when_publisherRuns() {
        // given
        UUID eventId = insertPendingEvent(BASE_TIME);

        // when
        try (LogCapture logs = LogCapture.attachTo(OutboxPublisher.class)) {
            publisher.publishBatch();

            // then
            assertThat(logs.messagesAt(Level.INFO))
                    .hasSize(1)
                    .allSatisfy(message -> assertThat(message).contains("OrderCreated", eventId.toString()));
        }
    }

    @Test
    void should_skipEvent_when_alreadyProcessed() {
        // given
        insertProcessedEvent(BASE_TIME);

        // when
        int published = publisher.publishBatch();

        // then
        assertThat(published).isZero();
    }

    @Test
    void should_processInCreatedAtOrder_when_multipleEventsPending() {
        // given
        insertPendingEvent(BASE_TIME.plusSeconds(30));
        UUID oldest = insertPendingEvent(BASE_TIME);
        UUID middle = insertPendingEvent(BASE_TIME.plusSeconds(15));

        // when
        publisher.publishBatch();

        // then
        assertThat(processedIds()).containsExactly(oldest, middle);
    }

    @Test
    void should_drainBacklogAcrossTicks_when_backlogExceedsBatchSize() {
        // given
        for (int i = 0; i < 5; i++) {
            insertPendingEvent(BASE_TIME.plusSeconds(i));
        }

        // when
        List<Integer> perTick = List.of(publisher.publishBatch(), publisher.publishBatch(),
                publisher.publishBatch(), publisher.publishBatch());

        // then
        assertThat(perTick).containsExactly(2, 2, 1, 0);
        assertThat(processedIds()).hasSize(5);
    }

    @Test
    void should_leaveRowsUnprocessed_when_tickFailsMidBatch() {
        // given
        insertPendingEvent(BASE_TIME);
        insertPendingEvent(BASE_TIME.plusSeconds(1));
        jdbcTemplate.execute(
                "alter table outbox_events add constraint temp_reject_processing check (processed_at is null)");

        // when
        try {
            assertThatThrownBy(() -> publisher.publishBatch()).isInstanceOf(DataAccessException.class);

            // then
            assertThat(countRowsIn("outbox_events")).isEqualTo(2);
            assertThat(processedIds()).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                    "select max(attempts) from outbox_events", Integer.class)).isZero();
        } finally {
            jdbcTemplate.execute("alter table outbox_events drop constraint temp_reject_processing");
        }
    }

    @Test
    void should_returnDisjointBatches_when_twoPollersRunConcurrently() throws Exception {
        // given
        for (int i = 0; i < 4; i++) {
            insertPendingEvent(BASE_TIME.plusSeconds(i));
        }
        CountDownLatch bothSelected = new CountDownLatch(2);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Callable<Set<UUID>> poll = () -> transactions.execute(status -> {
            Set<UUID> claimed = repository.findUnprocessedBatch(BATCH_SIZE).stream()
                    .map(OutboxEvent::getId)
                    .collect(Collectors.toSet());
            bothSelected.countDown();
            awaitQuietly(bothSelected);
            return claimed;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // when
        try {
            Future<Set<UUID>> first = pool.submit(poll);
            Future<Set<UUID>> second = pool.submit(poll);
            Set<UUID> firstBatch = first.get(30, TimeUnit.SECONDS);
            Set<UUID> secondBatch = second.get(30, TimeUnit.SECONDS);

            // then
            assertThat(firstBatch).hasSize(BATCH_SIZE);
            assertThat(secondBatch).hasSize(BATCH_SIZE);
            assertThat(firstBatch).doesNotContainAnyElementsOf(secondBatch);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private UUID insertPendingEvent(OffsetDateTime createdAt) {
        return insertEvent(createdAt, null);
    }

    private UUID insertProcessedEvent(OffsetDateTime createdAt) {
        return insertEvent(createdAt, createdAt.plusSeconds(1));
    }

    private UUID insertEvent(OffsetDateTime createdAt, OffsetDateTime processedAt) {
        UUID id = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into outbox_events
                    (id, aggregate_type, aggregate_id, event_type, payload, created_at, processed_at, attempts)
                values (?, 'Order', ?, 'OrderCreated', cast(? as jsonb), ?, cast(? as timestamptz), 0)
                """, id, aggregateId, "{\"orderId\":\"" + aggregateId + "\"}", createdAt, processedAt);
        return id;
    }

    private List<UUID> processedIds() {
        return jdbcTemplate.queryForList(
                "select id from outbox_events where processed_at is not null order by created_at, id", UUID.class);
    }

    private int attemptsOf(UUID eventId) {
        Integer attempts = jdbcTemplate.queryForObject(
                "select attempts from outbox_events where id = ?", Integer.class, eventId);
        return attempts == null ? 0 : attempts;
    }
}
