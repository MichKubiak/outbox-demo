package com.example.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import com.example.outbox.entity.OutboxEvent;
import com.example.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class OrderFlowIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @MockitoSpyBean
    private OutboxEventRepository outboxEventRepository;

    @Test
    void should_insertOrderAndOutboxEvent_when_orderCreated() {
        // given
        // when
        ResponseEntity<String> response = postOrder("123");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> order = jdbcTemplate.queryForMap("select id, customer_id from orders");
        Map<String, Object> event = jdbcTemplate.queryForMap(
                "select aggregate_type, aggregate_id, event_type, processed_at, attempts from outbox_events");
        assertThat(order).containsEntry("customer_id", "123");
        assertThat(event)
                .containsEntry("aggregate_type", "Order")
                .containsEntry("event_type", "OrderCreated")
                .containsEntry("aggregate_id", order.get("id"))
                .containsEntry("processed_at", null)
                .containsEntry("attempts", 0);
    }

    @Test
    void should_storeStatusAsLiteral_when_orderCreated() {
        // given
        // when
        postOrder("123");

        // then
        assertThat(jdbcTemplate.queryForObject("select status from orders", String.class)).isEqualTo("CREATED");
    }

    @Test
    void should_storePayloadWithOrderFields_when_orderCreated() throws Exception {
        // given
        // when
        ResponseEntity<String> response = postOrder("123");

        // then
        UUID orderId = UUID.fromString(JSON.readTree(response.getBody()).get("orderId").asText());
        String rawPayload = jdbcTemplate.queryForObject(
                "select cast(payload as text) from outbox_events", String.class);
        JsonNode payload = JSON.readTree(rawPayload);
        assertThat(payload.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(payload.get("customerId").asText()).isEqualTo("123");
        assertThat(payload.get("status").asText()).isEqualTo("CREATED");
        assertThat(payload.get("occurredAt").asText()).isNotBlank();
    }

    @Test
    void should_rollbackBothTables_when_outboxInsertFails() {
        // given
        willThrow(new DataIntegrityViolationException("outbox insert rejected"))
                .given(outboxEventRepository).save(any(OutboxEvent.class));

        // when
        ResponseEntity<String> response = postOrder("123");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(countRowsIn("orders")).isZero();
        assertThat(countRowsIn("outbox_events")).isZero();
    }

    @Test
    void should_hideInternalDetails_when_outboxInsertFails() {
        // given
        willThrow(new DataIntegrityViolationException("outbox insert rejected"))
                .given(outboxEventRepository).save(any(OutboxEvent.class));

        // when
        ResponseEntity<String> response = postOrder("123");

        // then
        assertThat(response.getBody())
                .contains("INTERNAL_ERROR")
                .doesNotContain("DataIntegrityViolationException", "outbox insert rejected", "com.example.outbox");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"{\"customerId\":\"\"}", "{\"customerId\":null}", "{}", "{"})
    void should_writeNothing_when_requestIsRejected(String body) {
        // given
        // when
        ResponseEntity<String> response = postJson(body);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countRowsIn("orders")).isZero();
        assertThat(countRowsIn("outbox_events")).isZero();
    }
}
