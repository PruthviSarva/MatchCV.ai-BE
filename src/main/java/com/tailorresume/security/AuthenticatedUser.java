package com.tailorresume.security;

import com.tailorresume.user.Role;

/**
 * The authenticated principal, rebuilt from the database on every request.
 *
 * <p>Holds only what authorization needs. Services that need the full account
 * load it from the repository by {@link #id()}.
 *
 * <p>The role is deliberately <em>not</em> read from the token. See
 * {@link JwtAuthenticationFilter} for why that distinction matters.
 */
public record AuthenticatedUser(Long id, String name, Role role) {

    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }
}
