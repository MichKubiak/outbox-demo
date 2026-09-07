package com.example.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.outbox.entity.OrderEntity;
import com.example.outbox.entity.OutboxEvent;
import com.example.outbox.support.TestEntities;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventFactoryTest {

    private static final Instant NOW = Instant.parse("2026-09-07T10:15:30Z");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    private ObjectMapper failingMapper;

    private OrderEntity order;

    @BeforeEach
    void setUp() {
        order = TestEntities.order(ORDER_ID, "123", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_serializePayloadWithOrderFields_when_orderGiven() throws Exception {
        // given
        OutboxEventFactory factory = new OutboxEventFactory(objectMapper, clock);

        // when
        OutboxEvent event = factory.orderCreated(order);

        // then
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("orderId").asText()).isEqualTo(ORDER_ID.toString());
        assertThat(payload.get("customerId").asText()).isEqualTo("123");
        assertThat(payload.get("status").asText()).isEqualTo("CREATED");
        assertThat(payload.get("occurredAt").asText()).startsWith("2026-09-07T10:15:30Z");
    }

    @Test
    void should_useInjectedClock_when_stampingOccurredAt() {
        // given
        OutboxEventFactory factory = new OutboxEventFactory(objectMapper, clock);

        // when
        OutboxEvent event = factory.orderCreated(order);

        // then
        assertThat(event.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_throwIllegalState_when_serializationFails() throws Exception {
        // given
        given(failingMapper.writeValueAsString(any()))
                .willThrow(new JsonMappingException(null, "payload not serializable"));
        OutboxEventFactory factory = new OutboxEventFactory(failingMapper, clock);

        // when
        // then
        assertThatThrownBy(() -> factory.orderCreated(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OrderCreated");
    }
}
