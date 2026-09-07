package com.example.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.outbox.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OrderTransactionBoundaryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int CONCURRENT_REQUESTS = 12;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MeterRegistry meterRegistry;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate transactions;

    @BeforeEach
    void prepareTransactionTemplate() {
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void should_hideBothRowsFromOtherConnections_when_transactionIsOpen() {
        // given
        // when
        transactions.executeWithoutResult(status -> {
            orderService.createOrder("123");
            entityManager.flush();

            // then
            assertThat(countOnSeparateConnection("orders")).isZero();
            assertThat(countOnSeparateConnection("outbox_events")).isZero();
        });
    }

    @Test
    void should_exposeBothRowsToOtherConnections_when_transactionCommitted() {
        // given
        transactions.executeWithoutResult(status -> orderService.createOrder("123"));

        // when
        // then
        assertThat(countOnSeparateConnection("orders")).isEqualTo(1);
        assertThat(countOnSeparateConnection("outbox_events")).isEqualTo(1);
    }

    @Test
    void should_persistNothing_when_callerRollsBack() {
        // given
        // when
        transactions.executeWithoutResult(status -> {
            orderService.createOrder("123");
            status.setRollbackOnly();
        });

        // then
        assertThat(countRowsIn("orders")).isZero();
        assertThat(countRowsIn("outbox_events")).isZero();
    }

    @Test
    void should_persistNothing_when_callerFailsAfterCreate() {
        // given
        // when
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            orderService.createOrder("123");
            throw new IllegalStateException("caller failed after create");
        })).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(countRowsIn("orders")).isZero();
        assertThat(countRowsIn("outbox_events")).isZero();
    }

    @Test
    void should_countOrder_when_transactionCommits() {
        // given
        double before = createdCount();

        // when
        transactions.executeWithoutResult(status -> orderService.createOrder("123"));

        // then
        assertThat(createdCount()).isEqualTo(before + 1);
    }

    @Test
    void should_notCountOrder_when_transactionRollsBack() {
        // given
        double before = createdCount();

        // when
        transactions.executeWithoutResult(status -> {
            orderService.createOrder("123");
            status.setRollbackOnly();
        });

        // then
        assertThat(createdCount()).isEqualTo(before);
    }

    @Test
    void should_writeOneEventPerOrder_when_requestsRunConcurrently() throws Exception {
        // given
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Callable<UUID>> calls = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            String customerId = "customer-" + i;
            calls.add(() -> orderIdOf(postOrder(customerId)));
        }

        // when
        List<UUID> orderIds = new ArrayList<>();
        try {
            for (Future<UUID> result : pool.invokeAll(calls, 60, TimeUnit.SECONDS)) {
                orderIds.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }

        // then
        assertThat(orderIds).doesNotContainNull().doesNotHaveDuplicates().hasSize(CONCURRENT_REQUESTS);
        assertThat(jdbcTemplate.queryForList("""
                select o.id from orders o
                join outbox_events e on e.aggregate_id = o.id and e.event_type = 'OrderCreated'
                """, UUID.class)).containsExactlyInAnyOrderElementsOf(orderIds);
    }

    private long countOnSeparateConnection(String table) {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            return pool.submit(() -> {
                Long count = new JdbcTemplate(dataSource)
                        .queryForObject("select count(*) from " + table, Long.class);
                return count == null ? 0L : count;
            }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while reading " + table, e);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + table + " on a separate connection", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private double createdCount() {
        return meterRegistry.get("orders.created").counter().count();
    }

    private static UUID orderIdOf(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(JSON.readTree(response.getBody()).get("orderId").asText());
    }
}
