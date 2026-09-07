package com.example.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OpenApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void should_serveApiDocs_when_springdocConfigured() throws Exception {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode info = JSON.readTree(response.getBody()).get("info");
        assertThat(info.get("title").asText()).isEqualTo("Outbox Pattern API");
        assertThat(info.get("version").asText()).isEqualTo("1.0.0");
    }

    @Test
    void should_deriveServerFromRequest_when_apiDocsRequested() throws Exception {
        // given
        // when
        JsonNode servers = apiDocs().get("servers");

        // then
        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).get("url").asText()).startsWith("http://localhost:" + port);
    }

    @Test
    void should_documentCreateOrderOperation_when_apiDocsRequested() throws Exception {
        // given
        // when
        JsonNode operation = apiDocs().at("/paths/~1orders/post");

        // then
        assertThat(operation.get("summary").asText()).isEqualTo("Create an order");
        assertThat(operation.get("tags").get(0).asText()).isEqualTo("Orders");
        assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/CreateOrderRequest");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"201", "400", "415", "429", "500"})
    void should_documentResponseStatus_when_apiDocsRequested(String status) throws Exception {
        // given
        // when
        JsonNode responses = apiDocs().at("/paths/~1orders/post/responses");

        // then
        assertThat(responses.has(status)).isTrue();
    }

    @Test
    void should_documentErrorSchema_when_apiDocsRequested() throws Exception {
        // given
        // when
        JsonNode error = apiDocs().at("/paths/~1orders/post/responses/400/content/application~1json/schema/$ref");

        // then
        assertThat(error.asText()).isEqualTo("#/components/schemas/ApiError");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"CreateOrderRequest", "OrderResponse", "ApiError"})
    void should_publishSchema_when_apiDocsRequested(String schema) throws Exception {
        // given
        // when
        JsonNode schemas = apiDocs().at("/components/schemas");

        // then
        assertThat(schemas.has(schema)).isTrue();
    }

    @Test
    void should_documentCustomerIdConstraints_when_apiDocsRequested() throws Exception {
        // given
        // when
        JsonNode customerId = apiDocs().at("/components/schemas/CreateOrderRequest/properties/customerId");

        // then
        assertThat(customerId.get("maxLength").asInt()).isEqualTo(64);
        assertThat(customerId.get("type").asText()).isEqualTo("string");
    }

    @Test
    void should_documentErrorCodes_when_apiDocsRequested() {
        // given
        // when
        String body = restTemplate.getForEntity("/v3/api-docs", String.class).getBody();

        // then
        assertThat(body).contains("VALIDATION_FAILED", "RATE_LIMIT_EXCEEDED", "INTERNAL_ERROR");
    }

    @Test
    void should_hideActuatorEndpoints_when_apiDocsRequested() throws Exception {
        // given
        // when
        JsonNode paths = apiDocs().get("paths");

        // then
        assertThat(paths.fieldNames()).toIterable().containsExactly("/orders");
    }

    @Test
    void should_serveSwaggerUi_when_uiRequested() {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("swagger-ui");
    }

    @Test
    void should_pointUiAtApiDocs_when_swaggerConfigRequested() {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs/swagger-config", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("/v3/api-docs");
    }

    private JsonNode apiDocs() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(response.getBody());
    }
}
