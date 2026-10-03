package com.weblab.rplace.weblab.rplace.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * What user_tokens keeps instead of a login link or session value: its SHA-256, in
 * lowercase hex. The values are 128 random bits, so a plain hash is enough (no salt,
 * no slow hash); someone who reads the table cannot log in with what they read.
 * Postgres computes the same value as encode(sha256(convert_to(value, 'UTF8')), 'hex').
 */
public final class TokenHashes {

    private TokenHashes() {
    }

    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether the value hashes to the stored hash, compared in constant time. */
    public static boolean matches(String value, String storedHash) {
        if (value == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256Hex(value).getBytes(StandardCharsets.US_ASCII), storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
