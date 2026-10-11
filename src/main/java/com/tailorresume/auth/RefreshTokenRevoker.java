package com.tailorresume.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Revokes a user's whole token family in its own transaction.
 *
 * <p>This exists purely so the revocation survives: reuse detection revokes and
 * then throws, and an inline repository call would be rolled back with the
 * aborted transaction — resurrecting the stolen family.
 */
@Service
public class RefreshTokenRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenRevoker(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUserId(Long userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }
}
