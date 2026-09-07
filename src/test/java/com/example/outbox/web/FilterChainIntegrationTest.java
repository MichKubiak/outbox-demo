package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.outbox.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class FilterChainIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String ORIGIN = "https://app.example.com";
    private static final String VALID_BODY = "{\"customerId\":\"123\"}";

    @Test
    void should_echoCorrelationId_when_headerAccepted() {
        // given
        // when
        ResponseEntity<String> response = postJson(VALID_BODY, Map.of(CorrelationIdFilter.HEADER, "trace-0001"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER)).isEqualTo("trace-0001");
    }

    @Test
    void should_generateCorrelationId_when_headerMissing() {
        // given
        // when
        ResponseEntity<String> response = postJson(VALID_BODY);

        // then
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER)).matches(UUID_PATTERN);
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @ValueSource(strings = {"bad id", "id_with_underscore", "id.with.dots", "id/with/slash"})
    @MethodSource("oversizedCorrelationIds")
    void should_generateCorrelationId_when_headerRejected(String incoming) {
        // given
        // when
        ResponseEntity<String> response = postJson(VALID_BODY, Map.of(CorrelationIdFilter.HEADER, incoming));

        // then
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER))
                .isNotEqualTo(incoming)
                .matches(UUID_PATTERN);
    }

    @Test
    void should_reuseCorrelationIdInErrorBody_when_requestRejected() throws Exception {
        // given
        // when
        ResponseEntity<String> response = postJson("{}", Map.of(CorrelationIdFilter.HEADER, "trace-0002"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(JSON.readTree(response.getBody()).get("correlationId").asText()).isEqualTo("trace-0002");
    }

    @Test
    void should_reuseCorrelationIdInErrorBody_when_methodNotAllowed() throws Exception {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.HEADER, "trace-0003");

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders", HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(JSON.readTree(response.getBody()).get("correlationId").asText()).isEqualTo("trace-0003");
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER)).isEqualTo("trace-0003");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"/actuator/health", "/ordersx", "/v3/api-docs"})
    void should_setSecurityHeaders_when_responseReturned(String path) {
        // given
        // when
        HttpHeaders headers = restTemplate.getForEntity(path, String.class).getHeaders();

        // then
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(headers.getFirst("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(headers.getFirst("Content-Security-Policy")).contains("default-src 'self'", "frame-ancestors 'none'");
    }

    @Test
    void should_setSecurityHeaders_when_orderCreated() {
        // given
        // when
        HttpHeaders headers = postJson(VALID_BODY).getHeaders();

        // then
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void should_reportRateLimitHeader_when_pathIsRateLimited() {
        // given
        // when
        ResponseEntity<String> response = postJson(VALID_BODY);

        // then
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isNotNull();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"/actuator/health", "/actuator/prometheus", "/v3/api-docs"})
    void should_skipRateLimitFilter_when_pathIsNotOrders(String path) {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);

        // then
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isNull();
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER)).isNotNull();
    }

    @Test
    void should_allowPreflight_when_postRequestedFromBrowser() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ORIGIN);
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name());
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type");

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGIN);
        assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.POST);
        assertThat(response.getHeaders().getAccessControlMaxAge()).isEqualTo(3600L);
    }

    @Test
    void should_rejectPreflight_when_methodNotAllowed() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ORIGIN);
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.DELETE.name());

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void should_exposeCorrelationHeaderToBrowser_when_orderCreatedFromBrowser() {
        // given
        // when
        ResponseEntity<String> response = postJson(VALID_BODY, Map.of(HttpHeaders.ORIGIN, ORIGIN));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGIN);
        assertThat(response.getHeaders().getAccessControlExposeHeaders())
                .contains(CorrelationIdFilter.HEADER, "Retry-After", "X-RateLimit-Remaining");
    }

    private static Stream<String> oversizedCorrelationIds() {
        return Stream.of("x".repeat(65));
    }
}
