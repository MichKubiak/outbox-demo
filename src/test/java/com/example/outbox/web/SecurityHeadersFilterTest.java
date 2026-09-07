package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/orders");
        response = new MockHttpServletResponse();
    }

    @ParameterizedTest(name = "{index}: {0}: {1}")
    @CsvSource({
            "X-Content-Type-Options,nosniff",
            "X-Frame-Options,DENY",
            "Referrer-Policy,no-referrer"
    })
    void should_setHeader_when_responseIsFiltered(String header, String expected) throws Exception {
        // given
        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getHeader(header)).isEqualTo(expected);
    }

    @Test
    void should_setContentSecurityPolicy_when_responseIsFiltered() throws Exception {
        // given
        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY)
                .contains("default-src 'self'", "frame-ancestors 'none'", "object-src 'none'");
    }

    @Test
    void should_setStrictTransportSecurity_when_responseIsFiltered() throws Exception {
        // given
        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getHeader("Strict-Transport-Security"))
                .isEqualTo(SecurityHeadersFilter.STRICT_TRANSPORT_SECURITY)
                .contains("max-age=31536000", "includeSubDomains");
    }

    @Test
    void should_continueChain_when_headersApplied() throws Exception {
        // given
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(chain.getResponse()).isSameAs(response);
    }

    @Test
    void should_overwriteHeader_when_valueAlreadyPresent() throws Exception {
        // given
        response.setHeader("X-Frame-Options", "SAMEORIGIN");

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getHeaders("X-Frame-Options")).containsExactly("DENY");
    }
}
