package com.example.outbox.service;

import com.example.outbox.entity.OrderEntity;
import com.example.outbox.repository.OrderRepository;
import com.example.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final Clock clock;
    private final Counter createdCounter;

    public OrderService(OrderRepository orderRepository, OutboxEventRepository outboxEventRepository,
                        OutboxEventFactory outboxEventFactory, Clock clock, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventFactory = outboxEventFactory;
        this.clock = clock;
        this.createdCounter = Counter.builder("orders.created")
                .description("Orders persisted together with their outbox event")
                .register(meterRegistry);
    }

    @Transactional
    public CreatedOrder createOrder(String customerId) {
        OrderEntity order = orderRepository.save(OrderEntity.create(customerId, OffsetDateTime.now(clock)));
        outboxEventRepository.save(outboxEventFactory.orderCreated(order));
        countAfterCommit();
        return new CreatedOrder(order.getId(), order.getCustomerId(), order.getStatus(), order.getCreatedAt());
    }

    private void countAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            createdCounter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                createdCounter.increment();
            }
        });
    }
}
