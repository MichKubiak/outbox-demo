package com.example.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.outbox.web.ClientIpResolver;
import com.example.outbox.web.CorrelationIdFilter;
import com.example.outbox.web.RateLimitFilter;
import com.example.outbox.web.RateLimitService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class FilterConfigTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-07T10:15:30Z"), ZoneOffset.UTC);

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    private FilterConfig config;

    @BeforeEach
    void setUp() {
        config = new FilterConfig();
    }

    @Test
    void should_runCorrelationIdFilterFirst_when_filtersRegistered() {
        // given
        // when
        FilterRegistrationBean<CorrelationIdFilter> registration = config.correlationIdFilterRegistration();

        // then
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
        assertThat(registration.getFilter()).isInstanceOf(CorrelationIdFilter.class);
    }

    @Test
    void should_runSecurityHeadersFilterAfterCorrelationId_when_filtersRegistered() {
        // given
        // when
        int securityHeadersOrder = config.securityHeadersFilterRegistration().getOrder();

        // then
        assertThat(securityHeadersOrder)
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 20)
                .isGreaterThan(config.correlationIdFilterRegistration().getOrder());
    }

    @Test
    void should_runRateLimitFilterLast_when_filtersRegistered() {
        // given
        RateLimitService rateLimitService = config.rateLimitService(properties(100), CLOCK);
        ClientIpResolver resolver = config.clientIpResolver(properties(100));

        // when
        FilterRegistrationBean<RateLimitFilter> registration =
                config.rateLimitFilterRegistration(rateLimitService, resolver, exceptionResolver);

        // then
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 30);
        assertThat(registration.getFilter()).isInstanceOf(RateLimitFilter.class);
    }

    @Test
    void should_limitRateLimitFilterToOrdersPath_when_filtersRegistered() {
        // given
        RateLimitService rateLimitService = config.rateLimitService(properties(100), CLOCK);
        ClientIpResolver resolver = config.clientIpResolver(properties(100));

        // when
        FilterRegistrationBean<RateLimitFilter> registration =
                config.rateLimitFilterRegistration(rateLimitService, resolver, exceptionResolver);

        // then
        assertThat(registration.getUrlPatterns()).containsExactly("/orders");
    }

    @Test
    void should_applyConfiguredCapacity_when_rateLimitServiceCreated() {
        // given
        RateLimitService service = config.rateLimitService(properties(1), CLOCK);

        // when
        boolean first = service.tryConsume("203.0.113.9").isConsumed();
        boolean second = service.tryConsume("203.0.113.9").isConsumed();

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void should_trustConfiguredProxies_when_clientIpResolverCreated() {
        // given
        ClientIpResolver resolver = config.clientIpResolver(properties(100));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    private static RateLimitProperties properties(int capacity) {
        return new RateLimitProperties(capacity, Duration.ofMinutes(1), List.of("10.0.0.0/8"), 1000,
                Duration.ofMinutes(5));
    }
}
