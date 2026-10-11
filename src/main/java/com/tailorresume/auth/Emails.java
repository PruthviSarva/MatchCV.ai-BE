package com.tailorresume.auth;

/**
 * The one definition of "the same email address".
 *
 * <p>Public because the admin console creates accounts too, and an address
 * normalized one way at signup and another way by an administrator would be
 * two different accounts as far as the unique index is concerned.
 *
 * <p>Lower-cased and trimmed, so casing and stray whitespace can neither create
 * a duplicate account nor stop a login from matching the row that exists.
 */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
