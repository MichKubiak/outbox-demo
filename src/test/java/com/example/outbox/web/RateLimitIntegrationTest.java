package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.outbox.AbstractPostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "ratelimit.capacity=5")
class RateLimitIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final int CAPACITY = 5;

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void forgetBuckets() {
        rateLimitService.clear();
    }

    @Test
    void should_returnTooManyRequests_when_limitExceeded() {
        // given
        List<HttpStatus> allowed = postOrders(CAPACITY);

        // when
        ResponseEntity<String> rejected = postOrder("123");

        // then
        assertThat(allowed).containsOnly(HttpStatus.CREATED);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void should_returnApiErrorBody_when_rateLimited() {
        // given
        postOrders(CAPACITY);

        // when
        ResponseEntity<String> rejected = postOrder("123");

        // then
        assertThat(rejected.getBody())
                .contains("RATE_LIMIT_EXCEEDED", "\"status\":429")
                .doesNotContain("Exception", "com.example.outbox");
    }

    @Test
    void should_returnRetryAfterHeader_when_rateLimited() {
        // given
        postOrders(CAPACITY);

        // when
        ResponseEntity<String> rejected = postOrder("123");

        // then
        assertThat(Long.parseLong(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, 60L);
        assertThat(rejected.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void should_keepPeerBucket_when_forwardedHeaderIsSpoofed() {
        // given
        for (int i = 0; i < CAPACITY; i++) {
            postJson("{\"customerId\":\"123\"}", Map.of("X-Forwarded-For", "203.0.113." + i));
        }

        // when
        ResponseEntity<String> rejected =
                postJson("{\"customerId\":\"123\"}", Map.of("X-Forwarded-For", "203.0.113.99"));

        // then
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void should_reportRemainingTokens_when_requestAllowed() {
        // given
        // when
        ResponseEntity<String> response = postOrder("123");

        // then
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo(Integer.toString(CAPACITY - 1));
    }

    private List<HttpStatus> postOrders(int times) {
        List<HttpStatus> statuses = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            statuses.add(HttpStatus.valueOf(postOrder("123").getStatusCode().value()));
        }
        return statuses;
    }
}
