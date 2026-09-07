package com.example.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@AutoConfigureObservability
class ObservabilityIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void should_incrementOrdersCreatedCounter_when_orderPosted() throws Exception {
        // given
        double before = counterValue("orders.created");

        // when
        postOrder("123");

        // then
        assertThat(counterValue("orders.created")).isEqualTo(before + 1);
    }

    @Test
    void should_tagMetricsWithApplicationAndEnvironment_when_registryCustomized() throws Exception {
        // given
        // when
        JsonNode tags = metric("orders.created").get("availableTags");

        // then
        assertThat(tagValues(tags, "application")).containsExactly("outbox");
        assertThat(tagValues(tags, "environment")).isNotEmpty();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"orders.created", "outbox.backlog.size", "hikaricp.connections.active",
            "jvm.memory.used", "http.server.requests"})
    void should_exposeMeter_when_metricsEndpointQueried(String meter) {
        // given
        postOrder("123");

        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics/" + meter, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_serveScrapeEndpoint_when_prometheusQueried() {
        // given
        // when
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getBody())
                .contains("orders_total", "outbox_backlog_size", "application=\"outbox\"");
    }

    @Test
    void should_publishRequestHistogram_when_orderPosted() {
        // given
        postOrder("123");

        // when
        String scrape = restTemplate.getForEntity("/actuator/prometheus", String.class).getBody();

        // then
        assertThat(scrape)
                .contains("http_server_requests_seconds_bucket", "uri=\"/orders\"", "le=");
    }

    @Test
    void should_notPublishPublisherMetrics_when_schedulerDisabled() {
        // given
        // when
        String scrape = restTemplate.getForEntity("/actuator/prometheus", String.class).getBody();

        // then
        assertThat(scrape).doesNotContain("outbox_events_published_total", "outbox_publish_duration_seconds");
    }

    private JsonNode metric(String meter) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics/" + meter, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(response.getBody());
    }

    private double counterValue(String meter) throws Exception {
        for (JsonNode measurement : metric(meter).get("measurements")) {
            if ("COUNT".equals(measurement.get("statistic").asText())) {
                return measurement.get("value").asDouble();
            }
        }
        throw new IllegalStateException("no COUNT measurement for " + meter);
    }

    private static List<String> tagValues(JsonNode availableTags, String tag) {
        for (JsonNode entry : availableTags) {
            if (tag.equals(entry.get("tag").asText())) {
                List<String> values = new ArrayList<>();
                for (JsonNode value : entry.get("values")) {
                    values.add(value.asText());
                }
                return values;
            }
        }
        throw new IllegalStateException("tag not found: " + tag);
    }
}
