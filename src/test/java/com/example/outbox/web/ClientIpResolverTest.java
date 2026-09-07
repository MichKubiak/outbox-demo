package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static final List<String> TRUSTED = List.of("10.0.0.0/8", "192.168.1.5");

    @Test
    void should_ignoreForwardedHeader_when_peerIsNotTrusted() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("203.0.113.9", "1.2.3.4");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void should_ignoreForwardedHeader_when_noTrustedProxyIsConfigured() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        MockHttpServletRequest request = request("10.0.0.7", "1.2.3.4");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @Test
    void should_useRightmostUntrustedEntry_when_peerIsTrusted() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("10.0.0.7", "1.2.3.4, 203.0.113.9, 10.0.0.8");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void should_fallBackToRemoteAddr_when_forwardedHeaderIsMalformed() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("10.0.0.7", "not-an-address");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @Test
    void should_fallBackToRemoteAddr_when_forwardedHeaderIsEmpty() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("10.0.0.7", "");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @Test
    void should_stripPort_when_forwardedEntryCarriesOne() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("10.0.0.7", "203.0.113.9:51234");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void should_normaliseToPrefix_when_addressIsIpv6() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        MockHttpServletRequest request = request("2001:db8:1:2:3:4:5:6", null);

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("2001:db8:1:2:0:0:0:0/64");
    }

    @Test
    void should_returnUnknown_when_remoteAddressIsMissing() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        MockHttpServletRequest request = request(null, null);

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo(ClientIpResolver.UNKNOWN);
    }

    @Test
    void should_useForwardedHeader_when_peerIsTrustedSingleAddress() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("192.168.1.5", "203.0.113.9");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void should_fallBackToPeer_when_wholeForwardedChainIsTrusted() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("10.0.0.7", "10.0.0.8, 10.0.0.9, 192.168.1.5");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @Test
    void should_fallBackToPeer_when_forwardedChainExceedsScanLimit() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        StringBuilder chain = new StringBuilder("203.0.113.9");
        for (int i = 1; i <= 17; i++) {
            chain.append(", 10.0.0.").append(i);
        }
        MockHttpServletRequest request = request("10.0.0.7", chain.toString());

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @Test
    void should_fallBackToPeer_when_rightmostForwardedEntryIsBlank() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("10.0.0.7", "203.0.113.9, ");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @ValueSource(strings = {"not-a-cidr", "10.0.0.0/33", "10.0.0.0/-1", "10.0.0.0/abc", "   "})
    void should_ignoreTrustedProxyEntry_when_entryIsUnparsable(String trustedProxy) {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of(trustedProxy));
        MockHttpServletRequest request = request("10.0.0.7", "203.0.113.9");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @Test
    void should_keepValidTrustedProxies_when_someEntriesAreUnparsable() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of("not-a-cidr", "10.0.0.0/8", "10.0.0.0/99"));
        MockHttpServletRequest request = request("10.0.0.7", "203.0.113.9");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void should_matchPartialByteMask_when_prefixIsNotByteAligned() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of("203.0.112.0/23"));
        MockHttpServletRequest request = request("203.0.113.9", "198.51.100.4");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("198.51.100.4");
    }

    @Test
    void should_rejectAddressOutsidePartialByteMask_when_prefixIsNotByteAligned() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of("203.0.112.0/23"));
        MockHttpServletRequest request = request("203.0.114.9", "198.51.100.4");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("203.0.114.9");
    }

    @Test
    void should_useForwardedHeader_when_trustedProxyIsIpv6() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of("2001:db8::/32"));
        MockHttpServletRequest request = request("2001:db8:0:0:0:0:0:1", "203.0.113.9");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("203.0.113.9");
    }

    @Test
    void should_normaliseBracketedIpv6_when_forwardedEntryCarriesPort() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("10.0.0.7", "[2001:db8:1:2:3:4:5:6]:443");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("2001:db8:1:2:0:0:0:0/64");
    }

    @Test
    void should_ignoreForwardedHeader_when_peerIsIpv4AndTrustListIsIpv6() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of("2001:db8::/32"));
        MockHttpServletRequest request = request("10.0.0.7", "203.0.113.9");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @Test
    void should_returnUnknown_when_remoteAddressIsBlank() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        MockHttpServletRequest request = request("   ", null);

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo(ClientIpResolver.UNKNOWN);
    }

    @Test
    void should_returnValueUnchanged_when_addressIsNotAnIpLiteral() {
        // given
        // when
        String normalised = ClientIpResolver.normalize("proxy-host");

        // then
        assertThat(normalised).isEqualTo("proxy-host");
    }

    @ParameterizedTest(name = "{index}: {0} -> {1}")
    @CsvSource({
            "203.0.113.9:8080,203.0.113.9",
            "'[2001:db8::1]:8080',2001:db8::1",
            "203.0.113.9,203.0.113.9",
            "2001:db8::1,2001:db8::1"
    })
    void should_removeTransportPort_when_entryCarriesOne(String raw, String expected) {
        // given
        // when
        String stripped = ClientIpResolver.stripPort(raw);

        // then
        assertThat(stripped).isEqualTo(expected);
    }

    @Test
    void should_fallBackToPeer_when_trustedPeerSendsNoForwardedHeader() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("10.0.0.7", null);

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("10.0.0.7");
    }

    @Test
    void should_returnPeerUnchanged_when_peerIsNotAnIpLiteral() {
        // given
        ClientIpResolver resolver = new ClientIpResolver(TRUSTED);
        MockHttpServletRequest request = request("proxy-host", "203.0.113.9");

        // when
        String resolved = resolver.resolve(request);

        // then
        assertThat(resolved).isEqualTo("proxy-host");
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @ValueSource(strings = {"[]", "[]:8080", "[2001:db8::1"})
    void should_returnValueUnchanged_when_bracketedAddressIsMalformed(String raw) {
        // given
        // when
        String stripped = ClientIpResolver.stripPort(raw);

        // then
        assertThat(stripped).isEqualTo(raw);
    }

    @Test
    void should_returnNull_when_strippedValueIsNull() {
        // given
        // when
        String stripped = ClientIpResolver.stripPort(null);

        // then
        assertThat(stripped).isNull();
    }

    @Test
    void should_returnEmpty_when_strippedValueIsEmpty() {
        // given
        // when
        String stripped = ClientIpResolver.stripPort("");

        // then
        assertThat(stripped).isEmpty();
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"1:2:3", "host:name", "not-an-address", "1.2.3.4.5", "::ffff:zzzz"})
    void should_returnNoAddress_when_valueIsNotAnIpLiteral(String value) {
        // given
        // when
        // then
        assertThat(ClientIpResolver.parse(value)).isNull();
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "10.0.0.0/33", "10.0.0.0/abc", "2001:db8::/129", "hostname/24"})
    void should_returnNoCidr_when_trustedProxyEntryIsInvalid(String value) {
        // given
        // when
        // then
        assertThat(ClientIpResolver.Cidr.parse(value)).isNull();
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @ValueSource(strings = {"10.0.0.0/8", "10.0.0.1", "2001:db8::/32", "0.0.0.0/0", "10.0.0.0/32"})
    void should_returnCidr_when_trustedProxyEntryIsValid(String value) {
        // given
        // when
        // then
        assertThat(ClientIpResolver.Cidr.parse(value)).isNotNull();
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader(ClientIpResolver.FORWARDED_HEADER, forwardedFor);
        }
        return request;
    }
}
