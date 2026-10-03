package com.weblab.rplace.weblab.rplace.core.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * The address a request came from. Bans, the hourly link limit and the pixel logs use it.
 *
 * <p>In production Place is reached only through Traefik, on the Dokploy overlay network.
 * X-Forwarded-For is worth reading exactly when the peer that opened the connection is
 * one of the proxies listed in PLACE_TRUSTED_PROXY_RANGES (default 10.0.1.0/24: Traefik's
 * address there changes when it is recreated, so the setting is a range). From any other
 * peer the header is ignored: anybody can send one.
 *
 * <p>When the peer is trusted the chain is read from the right, the end a proxy appends
 * to, and the first entry that is not itself a trusted proxy is the client. Entries to the
 * left of it were written by whoever sits behind that hop, so a made-up left entry never
 * becomes the address. A chain that is empty, made only of proxies, or broken by an entry
 * that is not an address falls back to the peer. The result is always a parsed address in
 * one spelling (IPv6 as RFC 5952), never header text. Same rules as core's clientip.
 */
@Component
public class ClientIp {

    public static final String SETTING = "PLACE_TRUSTED_PROXY_RANGES";

    public static final String DEFAULT_TRUSTED_PROXY_RANGES = "10.0.1.0/24";

    private static final Logger log = LoggerFactory.getLogger(ClientIp.class);

    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";

    private static final Pattern IPV4 = Pattern.compile(OCTET + "(\\." + OCTET + "){3}");

    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9A-Fa-f:.]+");

    private static final Pattern PORT = Pattern.compile("\\d{1,5}");

    private static final long IGNORED_HEADER_LOG_INTERVAL_MILLIS = 10 * 60 * 1000;

    private final String trustedProxies;

    private final List<Range> ranges;

    private final AtomicLong ignoredHeaders = new AtomicLong();

    private volatile long lastIgnoredHeaderLog;

    public ClientIp(@Value("${place.trusted-proxy-ranges:" + DEFAULT_TRUSTED_PROXY_RANGES + "}") String trustedProxies) {
        this.trustedProxies = trustedProxies == null || trustedProxies.isBlank()
                ? DEFAULT_TRUSTED_PROXY_RANGES
                : trustedProxies.trim();
        this.ranges = parseRanges(this.trustedProxies);
        log.info("Client address: X-Forwarded-For is read only from peers in {}={}", SETTING, this.trustedProxies);
    }

    /** The configured ranges, as written. */
    public String trustedProxies() {
        return trustedProxies;
    }

    public String of(HttpServletRequest request) {
        String rawPeer = request.getRemoteAddr();
        Optional<InetAddress> peer = parseAddress(rawPeer);
        List<String> forwarded = forwardedFor(request);

        if (peer.isPresent() && isTrustedProxy(peer.get())) {
            for (int i = forwarded.size() - 1; i >= 0; i--) {
                Optional<InetAddress> entry = parseAddress(forwarded.get(i));
                if (entry.isEmpty()) {
                    break;
                }
                if (!isTrustedProxy(entry.get())) {
                    return canonical(entry.get());
                }
            }
        } else if (!forwarded.isEmpty()) {
            noteIgnoredHeader();
        }
        return peer.map(ClientIp::canonical).orElse(rawPeer);
    }

    /** RFC 9110: repeated header lines are one comma-separated list. */
    private static List<String> forwardedFor(HttpServletRequest request) {
        List<String> entries = new ArrayList<>();
        for (String line : Collections.list(request.getHeaders("X-Forwarded-For"))) {
            for (String entry : line.split(",")) {
                if (!entry.isBlank()) {
                    entries.add(entry.trim());
                }
            }
        }
        return entries;
    }

    private boolean isTrustedProxy(InetAddress address) {
        for (Range range : ranges) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every request from a peer outside the ranges that still carries the header. If Traefik
     * itself were outside them, every request would land here and everyone would share
     * Traefik's address; this line in the log says so. No address is logged.
     */
    private void noteIgnoredHeader() {
        long count = ignoredHeaders.incrementAndGet();
        long now = System.currentTimeMillis();
        if (now - lastIgnoredHeaderLog >= IGNORED_HEADER_LOG_INTERVAL_MILLIS) {
            lastIgnoredHeaderLog = now;
            log.warn("Client address: ignored X-Forwarded-For on {} request(s) so far from peers outside {}={}",
                    count, SETTING, trustedProxies);
        }
    }

    /**
     * An address literal as a proxy may write it: bare, with a port, or bracketed IPv6 with
     * or without a port. Never a host name (no DNS lookup), never an address with a zone.
     */
    static Optional<InetAddress> parseAddress(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        if (text.startsWith("[")) {
            int end = text.indexOf(']');
            if (end < 0) {
                return Optional.empty();
            }
            String rest = text.substring(end + 1);
            if (!rest.isEmpty() && !(rest.startsWith(":") && PORT.matcher(rest.substring(1)).matches())) {
                return Optional.empty();
            }
            return parseIpv6(text.substring(1, end));
        }
        int colon = text.indexOf(':');
        if (colon >= 0 && colon == text.lastIndexOf(':')) {
            // a.b.c.d:port
            if (!PORT.matcher(text.substring(colon + 1)).matches()) {
                return Optional.empty();
            }
            return parseIpv4(text.substring(0, colon));
        }
        return colon >= 0 ? parseIpv6(text) : parseIpv4(text);
    }

    private static Optional<InetAddress> parseIpv4(String text) {
        if (!IPV4.matcher(text).matches()) {
            return Optional.empty();
        }
        return literal(text);
    }

    private static Optional<InetAddress> parseIpv6(String text) {
        if (!text.contains(":") || !IPV6_CHARACTERS.matcher(text).matches()) {
            return Optional.empty();
        }
        // Brackets make Java treat it as an IPv6 literal: an invalid one fails instead of going to DNS.
        return literal("[" + text + "]");
    }

    private static Optional<InetAddress> literal(String text) {
        try {
            return Optional.of(InetAddress.getByName(text));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    /** IPv4 dotted; IPv6 per RFC 5952 (lower case, longest run of zero groups as ::). */
    static String canonical(InetAddress address) {
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        byte[] bytes = address.getAddress();
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((bytes[2 * i] & 0xff) << 8) | (bytes[2 * i + 1] & 0xff);
        }
        int bestStart = -1;
        int bestLength = 1;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < 8 && groups[i] == 0) {
                i++;
            }
            if (i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                text.append("::");
                i += bestLength - 1;
                continue;
            }
            if (text.length() > 0 && text.charAt(text.length() - 1) != ':') {
                text.append(':');
            }
            text.append(Integer.toHexString(groups[i]));
        }
        return text.toString();
    }

    /**
     * A comma-separated list of CIDR ranges or single addresses. An entry that is neither
     * stops the application: a typo would otherwise quietly trust a header anyone can write,
     * or stop trusting the real proxy.
     */
    private static List<Range> parseRanges(String raw) {
        List<Range> parsed = new ArrayList<>();
        for (String field : raw.split(",")) {
            String entry = field.trim();
            if (entry.isEmpty()) {
                continue;
            }
            parsed.add(Range.parse(entry));
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(SETTING + " must list at least one CIDR range");
        }
        return List.copyOf(parsed);
    }

    private record Range(byte[] network, int prefixLength) {

        static Range parse(String entry) {
            String address = entry;
            Integer prefix = null;
            int slash = entry.indexOf('/');
            if (slash >= 0) {
                address = entry.substring(0, slash);
                String bits = entry.substring(slash + 1);
                if (!bits.matches("\\d{1,3}")) {
                    throw invalid(entry);
                }
                prefix = Integer.parseInt(bits);
            }
            Optional<InetAddress> parsed = address.contains(":") ? parseIpv6(address) : parseIpv4(address);
            if (parsed.isEmpty()) {
                throw invalid(entry);
            }
            byte[] bytes = parsed.get().getAddress();
            int length = prefix == null ? bytes.length * 8 : prefix;
            if (length > bytes.length * 8) {
                throw invalid(entry);
            }
            return new Range(bytes, length);
        }

        boolean contains(InetAddress candidate) {
            byte[] bytes = candidate.getAddress();
            if (bytes.length != network.length) {
                return false;
            }
            int full = prefixLength / 8;
            for (int i = 0; i < full; i++) {
                if (bytes[i] != network[i]) {
                    return false;
                }
            }
            int rest = prefixLength % 8;
            if (rest == 0) {
                return true;
            }
            int mask = (0xff << (8 - rest)) & 0xff;
            return (bytes[full] & mask) == (network[full] & mask);
        }

        private static IllegalArgumentException invalid(String entry) {
            return new IllegalArgumentException(SETTING + ": \"" + entry + "\" is neither a CIDR range nor an IP address");
        }
    }
}
