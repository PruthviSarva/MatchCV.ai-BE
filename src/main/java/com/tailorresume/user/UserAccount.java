package com.tailorresume.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A registered account. Named {@code UserAccount} rather than {@code User}
 * because {@code User} collides with Spring Security's own type.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Stored lower-cased; the unique index is on this exact value. */
    @Column(nullable = false, length = 255, unique = true)
    private String email;

    /** BCrypt hash with an algorithm prefix. Never a plaintext password. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Stored as the enum name, not its ordinal. Inserting a constant in the
     * middle of the enum would otherwise re-label every existing row.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected UserAccount() {
        // for JPA
    }

    private UserAccount(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role == null ? Role.USER : role;
    }

    public static UserAccount create(String name, String email, String passwordHash) {
        return new UserAccount(name, email, passwordHash, Role.USER);
    }

    /**
     * Only the admin console and the bootstrap seeder reach for this. Public
     * signup goes through {@link #create} and can never choose its own role.
     */
    public static UserAccount createWithRole(String name, String email, String passwordHash,
                                             Role role) {
        return new UserAccount(name, email, passwordHash, role);
    }

    /**
     * Timestamps are set here rather than left to the column defaults, so the
     * in-memory entity matches the row straight after a save.
     */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    /**
     * Turns the account off.
     *
     * <p>Flipping this flag is not on its own enough to lock someone out: an
     * access token already in their hands stays cryptographically valid until
     * it expires. {@code JwtAuthenticationFilter} re-reads this field on every
     * request, and the admin service revokes the refresh family, so the two
     * together make the change take effect immediately.
     */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
