package com.example.outbox.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutboxEventTest {

    private static final UUID AGGREGATE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.of(2026, 9, 7, 10, 15, 30, 0, ZoneOffset.UTC);
    private static final String PAYLOAD = "{\"orderId\":\"55555555-5555-5555-5555-555555555555\"}";

    @Test
    void should_copyAllFields_when_created() {
        // given
        // when
        OutboxEvent event = OutboxEvent.create("Order", AGGREGATE_ID, "OrderCreated", PAYLOAD, CREATED_AT);

        // then
        assertThat(event)
                .extracting(OutboxEvent::getAggregateType, OutboxEvent::getAggregateId, OutboxEvent::getEventType,
                        OutboxEvent::getPayload, OutboxEvent::getCreatedAt)
                .containsExactly("Order", AGGREGATE_ID, "OrderCreated", PAYLOAD, CREATED_AT);
    }

    @Test
    void should_beUnprocessed_when_created() {
        // given
        // when
        OutboxEvent event = event();

        // then
        assertThat(event.isProcessed()).isFalse();
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    void should_startWithZeroAttempts_when_created() {
        // given
        // when
        OutboxEvent event = event();

        // then
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void should_leaveIdUnset_when_notPersisted() {
        // given
        // when
        OutboxEvent event = event();

        // then
        assertThat(event.getId()).isNull();
    }

    @ParameterizedTest(name = "{index}: missing {0}")
    @MethodSource("missingArguments")
    void should_throwNullPointer_when_requiredArgumentIsNull(String field, String aggregateType, UUID aggregateId,
                                                             String eventType, String payload,
                                                             OffsetDateTime createdAt) {
        // given
        // when
        // then
        assertThatThrownBy(() -> OutboxEvent.create(aggregateType, aggregateId, eventType, payload, createdAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(field);
    }

    @Test
    void should_stampProcessedAt_when_markedProcessed() {
        // given
        OutboxEvent event = event();
        OffsetDateTime processedAt = CREATED_AT.plusSeconds(5);

        // when
        event.markProcessed(processedAt);

        // then
        assertThat(event.getProcessedAt()).isEqualTo(processedAt);
        assertThat(event.isProcessed()).isTrue();
    }

    @Test
    void should_countAttempt_when_markedProcessed() {
        // given
        OutboxEvent event = event();

        // when
        event.markProcessed(CREATED_AT.plusSeconds(5));

        // then
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void should_keepLatestTimestampAndCountBoth_when_markedProcessedTwice() {
        // given
        OutboxEvent event = event();
        OffsetDateTime second = CREATED_AT.plusSeconds(10);

        // when
        event.markProcessed(CREATED_AT.plusSeconds(5));
        event.markProcessed(second);

        // then
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getProcessedAt()).isEqualTo(second);
    }

    @Test
    void should_stayUnprocessed_when_processedAtIsNull() {
        // given
        OutboxEvent event = event();

        // when
        // then
        assertThatThrownBy(() -> event.markProcessed(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("processedAt");
        assertThat(event.isProcessed()).isFalse();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void should_keepPayloadVerbatim_when_payloadContainsUnicode() {
        // given
        String payload = "{\"customerId\":\"客户-123\",\"note\":\"ąćź\"}";

        // when
        OutboxEvent event = OutboxEvent.create("Order", AGGREGATE_ID, "OrderCreated", payload, CREATED_AT);

        // then
        assertThat(event.getPayload()).isEqualTo(payload);
    }

    @Test
    void should_acceptEmptyPayload_when_payloadIsBlank() {
        // given
        // when
        OutboxEvent event = OutboxEvent.create("Order", AGGREGATE_ID, "OrderCreated", "", CREATED_AT);

        // then
        assertThat(event.getPayload()).isEmpty();
    }

    private static Stream<Arguments> missingArguments() {
        return Stream.of(
                Arguments.of("aggregateType", null, AGGREGATE_ID, "OrderCreated", PAYLOAD, CREATED_AT),
                Arguments.of("aggregateId", "Order", null, "OrderCreated", PAYLOAD, CREATED_AT),
                Arguments.of("eventType", "Order", AGGREGATE_ID, null, PAYLOAD, CREATED_AT),
                Arguments.of("payload", "Order", AGGREGATE_ID, "OrderCreated", null, CREATED_AT),
                Arguments.of("createdAt", "Order", AGGREGATE_ID, "OrderCreated", PAYLOAD, null));
    }

    private static OutboxEvent event() {
        return OutboxEvent.create("Order", AGGREGATE_ID, "OrderCreated", PAYLOAD, CREATED_AT);
    }
}
