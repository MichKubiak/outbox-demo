package com.example.outbox.web;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.servlet.HandlerExceptionResolver;

public class RateLimitFilter implements Filter {

    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final HandlerExceptionResolver exceptionResolver;

    public RateLimitFilter(RateLimitService rateLimitService, ClientIpResolver clientIpResolver,
                           HandlerExceptionResolver exceptionResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        ConsumptionProbe probe = rateLimitService.tryConsume(clientIpResolver.resolve(httpRequest));
        if (probe.isConsumed()) {
            httpResponse.setHeader(REMAINING_HEADER, Long.toString(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        httpResponse.setHeader(REMAINING_HEADER, "0");
        exceptionResolver.resolveException(httpRequest, httpResponse, null,
                new RateLimitExceededException(secondsToWait(probe.getNanosToWaitForRefill())));
    }

    static long secondsToWait(long nanos) {
        return Math.max(1, (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND);
    }
}
