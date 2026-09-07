package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @ValueSource(strings = {"abc-123", "A", "0", "9f8b7c6d-1234-4321-abcd-0123456789ab"})
    void should_keepIncomingId_when_headerIsAccepted(String incoming) {
        // given
        // when
        String resolved = CorrelationIdFilter.resolve(incoming);

        // then
        assertThat(resolved).isEqualTo(incoming);
    }

    @Test
    void should_keepIncomingId_when_headerIsSixtyFourCharacters() {
        // given
        String incoming = "a".repeat(64);

        // when
        String resolved = CorrelationIdFilter.resolve(incoming);

        // then
        assertThat(resolved).isEqualTo(incoming);
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "has space", "under_score", "semi;colon", "new\nline", "<script>", "ąćź"})
    void should_generateUuid_when_headerIsRejected(String incoming) {
        // given
        // when
        String resolved = CorrelationIdFilter.resolve(incoming);

        // then
        assertThat(resolved).matches(UUID_PATTERN);
    }

    @Test
    void should_generateUuid_when_headerExceedsSixtyFourCharacters() {
        // given
        String incoming = "a".repeat(65);

        // when
        String resolved = CorrelationIdFilter.resolve(incoming);

        // then
        assertThat(resolved).matches(UUID_PATTERN).isNotEqualTo(incoming);
    }

    @Test
    void should_generateDistinctIds_when_calledRepeatedly() {
        // given
        // when
        String first = CorrelationIdFilter.resolve(null);
        String second = CorrelationIdFilter.resolve(null);

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_echoCorrelationIdHeader_when_requestCarriesOne() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    void should_setGeneratedCorrelationIdHeader_when_requestHasNone() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void should_continueChain_when_correlationIdApplied() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void should_exposeCorrelationIdInMdc_when_chainRuns() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        AtomicReference<String> insideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> insideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(insideChain.get()).isEqualTo("abc-123");
    }

    @Test
    void should_clearMdc_when_chainCompletes() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void should_clearMdc_when_chainThrows() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        FilterChain chain = (req, res) -> {
            throw new ServletException("downstream failure");
        };

        // when
        // then
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
