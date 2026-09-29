package com.weblab.rplace.weblab.rplace.core.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Unguessable values for login links, session cookies and login attempts. */
public final class RandomTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomTokens() {
    }

    // 128 random bits, URL-safe Base64 (the shape login links and sessions always had).
    public static String generate() {
        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().encodeToString(randomBytes);
    }
}
