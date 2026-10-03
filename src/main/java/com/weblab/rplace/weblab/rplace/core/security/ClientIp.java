package com.weblab.rplace.weblab.rplace.core.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The address a request came from, as Place has always read it: the first entry of
 * X-Forwarded-For (set by the proxy in front of Place), else the peer address.
 * Bans and the hourly link limit use it.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
