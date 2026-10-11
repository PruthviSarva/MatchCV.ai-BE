package com.tailorresume.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reads the JWT secret, which may be supplied either as base64 (what
 * {@code openssl rand -base64 48} produces) or as a raw passphrase.
 */
public final class Base64Secret {

    private Base64Secret() {
    }

    /** Decoded key bytes, falling back to the raw UTF-8 bytes if not base64. */
    public static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public static int decodedLength(String secret) {
        return decode(secret).length;
    }
}
