package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.outbox.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HttpErrorMappingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void should_returnNotFound_when_pathIsUnknown() {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/ordersx", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("NOT_FOUND");
    }

    @Test
    void should_returnNotFound_when_pathHasTrailingSlash() {
        // given
        // when
        ResponseEntity<String> response = restTemplate.postForEntity("/orders/", null, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(countRowsIn("orders")).isZero();
    }

    @Test
    void should_returnMethodNotAllowed_when_methodIsGet() {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/orders", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).contains("METHOD_NOT_ALLOWED");
    }

    @Test
    void should_returnApiErrorBody_when_statusIsFrameworkRaised() throws Exception {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/ordersx", String.class);

        // then
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("timestamp", "status", "code", "message", "path", "correlationId");
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("path").asText()).isEqualTo("/ordersx");
        assertThat(body.get("correlationId").asText()).isNotBlank();
    }

    @Test
    void should_hideStackTrace_when_statusIsFrameworkRaised() {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/ordersx", String.class);

        // then
        assertThat(response.getBody())
                .doesNotContain("Exception", "org.springframework", "com.example.outbox", "\tat ");
    }
}
