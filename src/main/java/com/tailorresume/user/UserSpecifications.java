package com.tailorresume.user;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Predicates for the admin user search. Each returns null when its filter is
 * absent, which Spring Data drops from the combined specification.
 */
public final class UserSpecifications {

    /**
     * The character that makes a literal {@code %} or {@code _} mean itself.
     *
     * <p>In Java source this single backslash is written with four, because the
     * value also travels through JPQL. Without it, searching for "a_b" matches
     * every address containing an "a", any character, then a "b" -- and a
     * search for "%" matches the entire table.
     */
    private static final char ESCAPE = '\\';

    private UserSpecifications() {
    }

    /**
     * Case-insensitive contains, over name and email together.
     *
     * <p>The wildcards are added here and both sides are lower-cased, so an
     * address pasted with odd casing still finds its row.
     */
    public static Specification<UserAccount> matching(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("name")), pattern, ESCAPE),
                builder.like(builder.lower(root.get("email")), pattern, ESCAPE));
    }

    public static Specification<UserAccount> active(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, builder) -> builder.equal(root.get("active"), active);
    }

    public static Specification<UserAccount> role(Role role) {
        if (role == null) {
            return null;
        }
        return (root, query, builder) -> builder.equal(root.get("role"), role);
    }

    /** Escapes the backslash first, or it would escape the escapes added after it. */
    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
