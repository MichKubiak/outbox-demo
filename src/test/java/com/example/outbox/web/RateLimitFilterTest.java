package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final String CLIENT_IP = "203.0.113.9";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    @Mock
    private FilterChain chain;

    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimitService, clientIpResolver, exceptionResolver);
        request = new MockHttpServletRequest("POST", "/orders");
        response = new MockHttpServletResponse();
    }

    @Test
    void should_continueChain_when_tokenIsConsumed() throws Exception {
        // given
        givenProbe(ConsumptionProbe.consumed(42L, 0L));

        // when
        filter.doFilter(request, response, chain);

        // then
        then(chain).should().doFilter(request, response);
    }

    @Test
    void should_reportRemainingTokens_when_tokenIsConsumed() throws Exception {
        // given
        givenProbe(ConsumptionProbe.consumed(42L, 0L));

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("42");
    }

    @Test
    void should_notResolveException_when_tokenIsConsumed() throws Exception {
        // given
        givenProbe(ConsumptionProbe.consumed(1L, 0L));

        // when
        filter.doFilter(request, response, chain);

        // then
        then(exceptionResolver).should(never()).resolveException(any(), any(), any(), any());
    }

    @Test
    void should_blockChain_when_bucketIsEmpty() throws Exception {
        // given
        givenProbe(ConsumptionProbe.rejected(0L, NANOS_PER_SECOND, NANOS_PER_SECOND));

        // when
        filter.doFilter(request, response, chain);

        // then
        then(chain).should(never()).doFilter(any(), any());
    }

    @Test
    void should_reportZeroRemaining_when_bucketIsEmpty() throws Exception {
        // given
        givenProbe(ConsumptionProbe.rejected(0L, NANOS_PER_SECOND, NANOS_PER_SECOND));

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("0");
    }

    @Test
    void should_delegateToExceptionResolver_when_bucketIsEmpty() throws Exception {
        // given
        givenProbe(ConsumptionProbe.rejected(0L, 2 * NANOS_PER_SECOND, 2 * NANOS_PER_SECOND));

        // when
        filter.doFilter(request, response, chain);

        // then
        then(exceptionResolver).should()
                .resolveException(eq(request), eq(response), isNull(), exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue())
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Rate limit exceeded");
    }

    @Test
    void should_passRetryDelayToException_when_bucketIsEmpty() throws Exception {
        // given
        givenProbe(ConsumptionProbe.rejected(0L, 3 * NANOS_PER_SECOND, 3 * NANOS_PER_SECOND));

        // when
        filter.doFilter(request, response, chain);

        // then
        then(exceptionResolver).should()
                .resolveException(any(), any(), isNull(), exceptionCaptor.capture());
        RateLimitExceededException failure = (RateLimitExceededException) exceptionCaptor.getValue();
        assertThat(failure.getSecondsToWait()).isEqualTo(3L);
    }

    @Test
    void should_keyBucketByResolvedClientIp_when_filtering() throws Exception {
        // given
        givenProbe(ConsumptionProbe.consumed(5L, 0L));

        // when
        filter.doFilter(request, response, chain);

        // then
        then(clientIpResolver).should().resolve(request);
        then(rateLimitService).should().tryConsume(CLIENT_IP);
    }

    @ParameterizedTest(name = "{index}: {0}ns -> {1}s")
    @CsvSource({
            "0,1",
            "1,1",
            "999999999,1",
            "1000000000,1",
            "1000000001,2",
            "1500000000,2",
            "59000000000,59",
            "60000000000,60"
    })
    void should_roundUpToWholeSeconds_when_convertingRefillDelay(long nanos, long expectedSeconds) {
        // given
        // when
        long seconds = RateLimitFilter.secondsToWait(nanos);

        // then
        assertThat(seconds).isEqualTo(expectedSeconds);
    }

    private void givenProbe(ConsumptionProbe probe) {
        given(clientIpResolver.resolve(request)).willReturn(CLIENT_IP);
        given(rateLimitService.tryConsume(CLIENT_IP)).willReturn(probe);
    }
}
