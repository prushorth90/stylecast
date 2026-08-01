package com.stylecast.user;

import java.util.Locale;

/**
 * Normalizes an email address the same way everywhere it's used as a
 * lookup key (registration, login, uniqueness checks) - trimmed and
 * lowercased - so "User@Example.com " and "user@example.com" are always
 * treated as the same account.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
