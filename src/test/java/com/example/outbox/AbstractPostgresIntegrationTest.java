package com.example.outbox;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "outbox.publisher.enabled=false",
        "ratelimit.capacity=100000"
})
@Tag("integration")
public abstract class AbstractPostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("outbox")
            .withUsername("outbox")
            .withPassword("outbox");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void truncateOutboxTables() {
        jdbcTemplate.execute("truncate table orders, outbox_events");
    }

    protected ResponseEntity<String> postOrder(String customerId) {
        return postJson("{\"customerId\":\"" + customerId + "\"}");
    }

    protected ResponseEntity<String> postJson(String body) {
        return restTemplate.postForEntity("/orders", jsonEntity(body, Map.of()), String.class);
    }

    protected ResponseEntity<String> postJson(String body, Map<String, String> extraHeaders) {
        return restTemplate.postForEntity("/orders", jsonEntity(body, extraHeaders), String.class);
    }

    protected long countRowsIn(String table) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0L : count;
    }

    private static HttpEntity<String> jsonEntity(String body, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        extraHeaders.forEach(headers::set);
        return new HttpEntity<>(body, headers);
    }
}
