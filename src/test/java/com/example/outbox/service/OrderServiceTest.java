package com.example.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.example.outbox.entity.OrderEntity;
import com.example.outbox.entity.OrderStatus;
import com.example.outbox.entity.OutboxEvent;
import com.example.outbox.repository.OrderRepository;
import com.example.outbox.repository.OutboxEventRepository;
import com.example.outbox.support.TestEntities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-07T10:15:30Z");
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Captor
    private ArgumentCaptor<OutboxEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<OrderEntity> orderCaptor;

    private SimpleMeterRegistry meterRegistry;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OutboxEventFactory factory = new OutboxEventFactory(objectMapper, clock);
        orderService = new OrderService(orderRepository, outboxEventRepository, factory, clock, meterRegistry);
    }

    @Test
    void should_saveOrderAndOutboxEvent_when_requestIsValid() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());

        // when
        orderService.createOrder("123");

        // then
        then(outboxEventRepository).should().save(any(OutboxEvent.class));
    }

    @Test
    void should_useOrderCreatedEventType_when_orderSaved() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());

        // when
        orderService.createOrder("123");

        // then
        then(outboxEventRepository).should().save(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .extracting(OutboxEvent::getEventType, OutboxEvent::getAggregateType, OutboxEvent::getAggregateId)
                .containsExactly("OrderCreated", "Order", ORDER_ID);
    }

    @Test
    void should_returnCreatedOrderWithGeneratedId_when_orderSaved() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());

        // when
        CreatedOrder created = orderService.createOrder("123");

        // then
        assertThat(created).isEqualTo(new CreatedOrder(ORDER_ID, "123", OrderStatus.CREATED,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)));
    }

    @Test
    void should_countCreatedOrder_when_orderCommitted() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());

        // when
        orderService.createOrder("123");

        // then
        assertThat(meterRegistry.get("orders.created").counter().count()).isEqualTo(1.0);
    }

    @Test
    void should_propagateException_when_outboxSaveFails() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());
        given(outboxEventRepository.save(any(OutboxEvent.class)))
                .willThrow(new DataIntegrityViolationException("outbox insert failed"));

        // when
        // then
        assertThatThrownBy(() -> orderService.createOrder("123"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_notCountOrder_when_outboxSaveFails() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());
        given(outboxEventRepository.save(any(OutboxEvent.class)))
                .willThrow(new DataIntegrityViolationException("outbox insert failed"));

        // when
        assertThatThrownBy(() -> orderService.createOrder("123")).isInstanceOf(RuntimeException.class);

        // then
        assertThat(meterRegistry.get("orders.created").counter().count()).isEqualTo(0.0);
    }

    @Test
    void should_rejectNullCustomerId_when_orderIsBuilt() {
        // given
        // when
        // then
        assertThatThrownBy(() -> orderService.createOrder(null)).isInstanceOf(NullPointerException.class);
        then(outboxEventRepository).should(never()).save(any(OutboxEvent.class));
    }

    @Test
    void should_deferCounter_when_transactionIsStillOpen() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());
        TransactionSynchronizationManager.initSynchronization();

        // when
        try {
            orderService.createOrder("123");

            // then
            assertThat(meterRegistry.get("orders.created").counter().count()).isEqualTo(0.0);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void should_countCreatedOrder_when_transactionCommits() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());
        TransactionSynchronizationManager.initSynchronization();

        // when
        try {
            orderService.createOrder("123");
            TransactionSynchronizationUtils.triggerAfterCommit();

            // then
            assertThat(meterRegistry.get("orders.created").counter().count()).isEqualTo(1.0);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void should_useInjectedClock_when_stampingCreatedAt() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());

        // when
        orderService.createOrder("123");

        // then
        then(orderRepository).should().save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getCreatedAt())
                .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_saveOrderBeforeOutboxEvent_when_requestIsValid() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());
        InOrder order = inOrder(orderRepository, outboxEventRepository);

        // when
        orderService.createOrder("123");

        // then
        order.verify(orderRepository).save(any(OrderEntity.class));
        order.verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void should_linkEventPayloadToOrder_when_orderSaved() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());

        // when
        orderService.createOrder("123");

        // then
        then(outboxEventRepository).should().save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getPayload())
                .contains(ORDER_ID.toString(), "\"customerId\":\"123\"", "CREATED");
    }

    @Test
    void should_leaveEventUnprocessed_when_orderCreated() {
        // given
        given(orderRepository.save(any(OrderEntity.class))).willReturn(savedOrder());

        // when
        orderService.createOrder("123");

        // then
        then(outboxEventRepository).should().save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isProcessed()).isFalse();
        assertThat(eventCaptor.getValue().getAttempts()).isZero();
    }

    private OrderEntity savedOrder() {
        return TestEntities.order(ORDER_ID, "123", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }
}
