package com.tailorresume.user;

import java.time.Instant;

/**
 * The account as the frontend sees it. Never carries the password hash.
 *
 * <p>{@code role} is here because the navigation has to know whether to offer
 * the admin console, and {@code active} because a disabled account that somehow
 * still holds a token should be told why it is stuck rather than shown an
 * empty app.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean active,
        Instant createdAt,
        Instant lastLoginAt) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
