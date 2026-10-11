package com.tailorresume.auth;

import com.tailorresume.common.ApiException;
import com.tailorresume.config.AppProperties;
import com.tailorresume.user.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <ul>
 *   <li>The token is an opaque 256-bit random string, not a JWT, precisely so
 *       it can be revoked server-side.</li>
 *   <li>Only its SHA-256 hash is persisted. SHA-256 rather than BCrypt is
 *       correct here: the input is already high-entropy random, so there is
 *       nothing to brute-force, and lookup must be an indexed equality match.</li>
 *   <li>Rotation is mandatory — every refresh revokes the presented token. A
 *       revoked token presented again revokes the whole family, since that
 *       means either theft or a replay.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final RefreshTokenRevoker revoker;
    private final AppProperties.Jwt config;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               RefreshTokenRevoker revoker,
                               AppProperties properties) {
        this.repository = repository;
        this.revoker = revoker;
        this.config = properties.jwt();
    }

    /** The raw token, returned to the client exactly once. */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record RotationResult(UserAccount user, IssuedToken token) {
    }

    @Transactional
    public IssuedToken issue(UserAccount user) {
        String raw = randomToken();
        Instant expiresAt = Instant.now().plus(config.refreshExpiration());
        repository.save(RefreshToken.issue(user, hash(raw), expiresAt));
        return new IssuedToken(raw, expiresAt);
    }

    /**
     * Verifies and rotates, returning the replacement token.
     *
     * @throws ApiException 401 if the token is unknown, expired or already used
     */
    @Transactional
    public RotationResult rotate(String presentedToken) {
        RefreshToken existing = repository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token."));

        if (existing.isRevoked()) {
            // Someone is replaying a token that was already rotated: stolen, or
            // a client retried. Both warrant killing every session. The
            // revocation goes through the revoker so it commits in its own
            // transaction and is not rolled back by the throw below.
            log.warn("Refresh token reuse detected for user {}; revoking all sessions",
                    existing.getUser().getId());
            revoker.revokeAllForUserId(existing.getUser().getId());
            throw ApiException.unauthorized("Session expired. Please log in again.");
        }

        if (existing.isExpired()) {
            throw ApiException.unauthorized("Session expired. Please log in again.");
        }

        UserAccount user = existing.getUser();
        if (!user.isActive()) {
            throw ApiException.unauthorized("This account is disabled.");
        }

        String raw = randomToken();
        Instant expiresAt = Instant.now().plus(config.refreshExpiration());
        RefreshToken replacement =
                repository.save(RefreshToken.issue(user, hash(raw), expiresAt));

        existing.replaceWith(replacement);
        repository.save(existing);

        return new RotationResult(user, new IssuedToken(raw, expiresAt));
    }

    /** Logout. Silently ignores an unknown token — logout must not leak validity. */
    @Transactional
    public void revoke(String presentedToken) {
        repository.findByTokenHash(hash(presentedToken)).ifPresent(token -> {
            token.revoke();
            repository.save(token);
        });
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex SHA-256, exactly 64 characters — matches the column width. */
    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
