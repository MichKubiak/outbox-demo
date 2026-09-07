package com.example.outbox.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ClientIpResolver {

    static final String FORWARDED_HEADER = "X-Forwarded-For";
    static final String UNKNOWN = "unknown";

    private static final int MAX_FORWARDED_ENTRIES = 16;
    private static final int IPV6_PREFIX_BYTES = 8;
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final List<Cidr> trustedProxies;

    public ClientIpResolver(List<String> trustedProxies) {
        List<Cidr> parsed = new ArrayList<>();
        for (String candidate : trustedProxies) {
            Cidr cidr = Cidr.parse(candidate);
            if (cidr != null) {
                parsed.add(cidr);
            }
        }
        this.trustedProxies = List.copyOf(parsed);
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (peer == null || peer.isBlank()) {
            return UNKNOWN;
        }
        if (trustedProxies.isEmpty() || !isTrusted(peer)) {
            return normalize(peer);
        }
        String header = request.getHeader(FORWARDED_HEADER);
        if (header == null || header.isBlank()) {
            return normalize(peer);
        }
        return fromForwardedHeader(header, peer);
    }

    private String fromForwardedHeader(String header, String peer) {
        String[] entries = header.split(",");
        int stop = Math.max(0, entries.length - MAX_FORWARDED_ENTRIES);
        for (int i = entries.length - 1; i >= stop; i--) {
            String candidate = stripPort(entries[i].trim());
            if (parse(candidate) == null) {
                return normalize(peer);
            }
            if (!isTrusted(candidate)) {
                return normalize(candidate);
            }
        }
        return normalize(peer);
    }

    private boolean isTrusted(String address) {
        InetAddress parsed = parse(stripPort(address));
        if (parsed == null) {
            return false;
        }
        for (Cidr cidr : trustedProxies) {
            if (cidr.matches(parsed)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String address) {
        InetAddress parsed = parse(stripPort(address));
        if (parsed == null) {
            return address;
        }
        byte[] bytes = parsed.getAddress();
        if (bytes.length != 16) {
            return parsed.getHostAddress();
        }
        byte[] prefix = new byte[16];
        System.arraycopy(bytes, 0, prefix, 0, IPV6_PREFIX_BYTES);
        try {
            return InetAddress.getByAddress(prefix).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return parsed.getHostAddress();
        }
    }

    static String stripPort(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.charAt(0) == '[') {
            int end = value.indexOf(']');
            return end > 1 ? value.substring(1, end) : value;
        }
        int colon = value.indexOf(':');
        if (colon > 0 && value.indexOf(':', colon + 1) < 0) {
            return value.substring(0, colon);
        }
        return value;
    }

    static InetAddress parse(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (IPV4.matcher(value).matches()) {
            return literal(value);
        }
        if (value.indexOf(':') >= 0 && IPV6_CHARS.matcher(value).matches()) {
            return literal(value);
        }
        return null;
    }

    private static InetAddress literal(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    static final class Cidr {

        private final byte[] network;
        private final int prefixLength;

        private Cidr(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static Cidr parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String trimmed = value.trim();
            int slash = trimmed.indexOf('/');
            String host = slash < 0 ? trimmed : trimmed.substring(0, slash);
            InetAddress address = ClientIpResolver.parse(host);
            if (address == null) {
                return null;
            }
            int bits = address.getAddress().length * 8;
            if (slash < 0) {
                return new Cidr(address.getAddress(), bits);
            }
            int prefix;
            try {
                prefix = Integer.parseInt(trimmed.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
            if (prefix < 0 || prefix > bits) {
                return null;
            }
            return new Cidr(address.getAddress(), prefix);
        }

        boolean matches(InetAddress candidate) {
            byte[] address = candidate.getAddress();
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
