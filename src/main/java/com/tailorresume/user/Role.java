package com.tailorresume.user;

/**
 * What an account is allowed to do.
 *
 * <p>Stored as a string column rather than an ordinal: an ordinal silently
 * remaps every existing row the moment a constant is inserted in the middle.
 *
 * <p>There are deliberately only two. Anything finer-grained is a permission
 * model, and this application does not have enough distinct actions to need
 * one — an admin can see and manage accounts, a user can only touch their own.
 */
public enum Role {

    USER,
    ADMIN;

    /** The authority name Spring Security expects for {@code hasRole("ADMIN")}. */
    public String authority() {
        return "ROLE_" + name();
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
