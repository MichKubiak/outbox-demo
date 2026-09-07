package com.example.outbox.config;

import com.example.outbox.web.ClientIpResolver;
import com.example.outbox.web.CorrelationIdFilter;
import com.example.outbox.web.RateLimitFilter;
import com.example.outbox.web.RateLimitService;
import com.example.outbox.web.SecurityHeadersFilter;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class FilterConfig {

    private static final String RATE_LIMITED_PATH = "/orders";
    private static final int CORRELATION_ID_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    private static final int SECURITY_HEADERS_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;
    private static final int RATE_LIMIT_ORDER = Ordered.HIGHEST_PRECEDENCE + 30;

    @Bean
    public ClientIpResolver clientIpResolver(RateLimitProperties properties) {
        return new ClientIpResolver(properties.trustedProxies());
    }

    @Bean
    public RateLimitService rateLimitService(RateLimitProperties properties, Clock clock) {
        return new RateLimitService(properties, clock);
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(CORRELATION_ID_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration() {
        FilterRegistrationBean<SecurityHeadersFilter> registration =
                new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.setOrder(SECURITY_HEADERS_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitService rateLimitService, ClientIpResolver clientIpResolver,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        RateLimitFilter filter = new RateLimitFilter(rateLimitService, clientIpResolver, exceptionResolver);
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(RATE_LIMITED_PATH);
        registration.setOrder(RATE_LIMIT_ORDER);
        return registration;
    }
}
