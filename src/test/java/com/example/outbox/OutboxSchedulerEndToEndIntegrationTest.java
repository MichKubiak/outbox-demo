package com.example.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import com.example.outbox.service.OutboxPublisher;
import com.example.outbox.service.OutboxPublisherScheduler;
import com.example.outbox.support.LogCapture;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "outbox.publisher.enabled=true",
        "outbox.publisher.fixed-delay-ms=200"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxSchedulerEndToEndIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL = Duration.ofMillis(50);

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void should_registerSchedulerBean_when_publisherEnabled() {
        // given
        // when
        String[] beans = applicationContext.getBeanNamesForType(OutboxPublisherScheduler.class);

        // then
        assertThat(beans).hasSize(1);
    }

    @Test
    void should_markEventProcessed_when_orderPostedOverHttp() {
        // given
        ResponseEntity<String> response = postOrder("123");

        // when
        awaitProcessedCount(1);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbcTemplate.queryForMap(
                "select attempts, processed_at >= created_at as after_creation from outbox_events"))
                .containsEntry("attempts", 1)
                .containsEntry("after_creation", true);
    }

    @Test
    void should_logEventPayload_when_schedulerTicks() throws Exception {
        // given
        try (LogCapture logs = LogCapture.attachTo(OutboxPublisher.class)) {
            UUID orderId = orderIdOf(postOrder("123"));

            // when
            awaitProcessedCount(1);

            // then
            assertThat(logs.messagesAt(Level.INFO))
                    .hasSize(1)
                    .allSatisfy(message -> assertThat(message)
                            .contains("Outbox event published", "OrderCreated", orderId.toString(), "\"customerId\""));
        }
    }

    @Test
    void should_incrementPublishedCounter_when_schedulerTicks() {
        // given
        double before = publishedCount();

        // when
        postOrder("123");
        awaitProcessedCount(1);

        // then
        await().atMost(TIMEOUT).pollInterval(POLL)
                .untilAsserted(() -> assertThat(publishedCount()).isEqualTo(before + 1));
    }

    @Test
    void should_resetBacklogGauge_when_backlogDrained() {
        // given
        postOrder("123");

        // when
        awaitProcessedCount(1);

        // then
        await().atMost(TIMEOUT).pollInterval(POLL)
                .untilAsserted(() -> assertThat(backlogGauge()).isZero());
    }

    @Test
    void should_publishOneEventPerOrder_when_severalOrdersPosted() throws Exception {
        // given
        List<UUID> orderIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            orderIds.add(orderIdOf(postOrder("customer-" + i)));
        }

        // when
        awaitProcessedCount(orderIds.size());

        // then
        assertThat(jdbcTemplate.queryForList(
                "select aggregate_id from outbox_events where processed_at is not null", UUID.class))
                .containsExactlyInAnyOrderElementsOf(orderIds);
    }

    @Test
    void should_keepEventUntouched_when_furtherTicksRun() {
        // given
        postOrder("123");
        awaitProcessedCount(1);
        OffsetDateTime processedAt = jdbcTemplate.queryForObject(
                "select processed_at from outbox_events", OffsetDateTime.class);

        // when
        awaitTicks(3);

        // then
        Map<String, Object> event = jdbcTemplate.queryForMap("select attempts, processed_at from outbox_events");
        assertThat(event.get("attempts")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select processed_at from outbox_events", OffsetDateTime.class))
                .isEqualTo(processedAt);
    }

    @Test
    void should_stayIdle_when_noEventsPending() {
        // given
        double before = publishedCount();

        // when
        awaitTicks(3);

        // then
        assertThat(publishedCount()).isEqualTo(before);
        assertThat(publisher.currentBacklogGauge()).isZero();
    }

    private void awaitProcessedCount(int expected) {
        await().atMost(TIMEOUT).pollInterval(POLL).until(() -> countProcessedEvents() == expected);
    }

    private void awaitTicks(int ticks) {
        long before = tickCount();
        await().atMost(TIMEOUT).pollInterval(POLL).until(() -> tickCount() >= before + ticks);
    }

    private long countProcessedEvents() {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where processed_at is not null", Long.class);
        return count == null ? 0L : count;
    }

    private long tickCount() {
        return meterRegistry.get("outbox.publish.duration").timer().count();
    }

    private double publishedCount() {
        return meterRegistry.get("outbox.events.published").counter().count();
    }

    private double backlogGauge() {
        return meterRegistry.get("outbox.backlog.size").gauge().value();
    }

    private static UUID orderIdOf(ResponseEntity<String> response) throws Exception {
        return UUID.fromString(JSON.readTree(response.getBody()).get("orderId").asText());
    }
}
