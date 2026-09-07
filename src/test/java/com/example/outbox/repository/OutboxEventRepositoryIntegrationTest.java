package com.example.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.outbox.AbstractPostgresIntegrationTest;
import com.example.outbox.entity.OrderEntity;
import com.example.outbox.entity.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxEventRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2026, 9, 7, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String PAYLOAD =
            "{\"orderId\":\"11111111-1111-1111-1111-111111111111\",\"customerId\":\"123\",\"status\":\"CREATED\"}";
    private static final int BATCH_SIZE = 10;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @BeforeEach
    void prepareTransactionTemplate() {
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void should_returnEmpty_when_noUnprocessedEvents() {
        // given
        saveEvent(BASE_TIME, true);

        // when
        // then
        assertThat(outboxEventRepository.findOldestUnprocessedCreatedAt()).isEmpty();
        assertThat(outboxEventRepository.countByProcessedAtIsNull()).isZero();
    }

    @Test
    void should_returnOldestCreatedAt_when_unprocessedEventsExist() {
        // given
        saveEvent(BASE_TIME.plusMinutes(5), false);
        saveEvent(BASE_TIME, false);
        saveEvent(BASE_TIME.plusMinutes(2), false);

        // when
        // then
        assertThat(outboxEventRepository.findOldestUnprocessedCreatedAt())
                .isPresent()
                .hasValueSatisfying(oldest -> assertThat(oldest).isEqualTo(BASE_TIME.toInstant()));
    }

    @Test
    void should_ignoreProcessedEvents_when_countingBacklog() {
        // given
        saveEvent(BASE_TIME, false);
        saveEvent(BASE_TIME.plusMinutes(1), false);
        saveEvent(BASE_TIME.plusMinutes(2), true);

        // when
        // then
        assertThat(outboxEventRepository.countByProcessedAtIsNull()).isEqualTo(2);
    }

    @Test
    void should_roundTripPayload_when_eventReloadedInNewTransaction() throws Exception {
        // given
        UUID eventId = saveEvent(BASE_TIME, false);

        // when
        OutboxEvent reloaded = transactions.execute(
                status -> outboxEventRepository.findById(eventId).orElseThrow());

        // then
        assertThat(JSON.readTree(reloaded.getPayload())).isEqualTo(JSON.readTree(PAYLOAD));
    }

    @Test
    void should_storePayloadAsJsonb_when_eventSaved() {
        // given
        UUID eventId = saveEvent(BASE_TIME, false);

        // when
        String customerId = jdbcTemplate.queryForObject(
                "select payload->>'customerId' from outbox_events where id = ?", String.class, eventId);

        // then
        assertThat(customerId).isEqualTo("123");
    }

    @Test
    void should_normalizeTimestampToUtc_when_offsetIsNotUtc() {
        // given
        UUID eventId = saveEvent(BASE_TIME.withOffsetSameInstant(ZoneOffset.ofHours(5)), false);

        // when
        OutboxEvent reloaded = transactions.execute(
                status -> outboxEventRepository.findById(eventId).orElseThrow());

        // then
        assertThat(reloaded.getCreatedAt().toInstant()).isEqualTo(BASE_TIME.toInstant());
        assertThat(reloaded.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void should_persistProcessedState_when_batchMarkedInsideTransaction() {
        // given
        UUID eventId = saveEvent(BASE_TIME, false);

        // when
        transactions.executeWithoutResult(status -> {
            List<OutboxEvent> batch = outboxEventRepository.findUnprocessedBatch(BATCH_SIZE);
            batch.forEach(event -> event.markProcessed(BASE_TIME.plusSeconds(3)));
        });

        // then
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select attempts, processed_at is not null as processed from outbox_events where id = ?", eventId);
        assertThat(row).containsEntry("attempts", 1).containsEntry("processed", true);
    }

    @Test
    void should_keepEventPending_when_transactionRollsBack() {
        // given
        UUID eventId = saveEvent(BASE_TIME, false);

        // when
        transactions.executeWithoutResult(status -> {
            outboxEventRepository.findUnprocessedBatch(BATCH_SIZE)
                    .forEach(event -> event.markProcessed(BASE_TIME.plusSeconds(3)));
            status.setRollbackOnly();
        });

        // then
        assertThat(outboxEventRepository.findById(eventId))
                .isPresent()
                .hasValueSatisfying(event -> assertThat(event.isProcessed()).isFalse());
    }

    @Test
    void should_rejectOrder_when_customerIdExceedsColumnLength() {
        // given
        String oversized = "x".repeat(65);

        // when
        // then
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> orderRepository.saveAndFlush(OrderEntity.create(oversized, BASE_TIME))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countRowsIn("orders")).isZero();
    }

    private UUID saveEvent(OffsetDateTime createdAt, boolean processed) {
        return transactions.execute(status -> {
            OutboxEvent event = OutboxEvent.create("Order", UUID.randomUUID(), "OrderCreated", PAYLOAD, createdAt);
            if (processed) {
                event.markProcessed(createdAt.plusSeconds(1));
            }
            return outboxEventRepository.save(event).getId();
        });
    }
}
