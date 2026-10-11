package com.tailorresume.security;

import com.tailorresume.config.AppProperties;
import com.tailorresume.user.UserAccount;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Issues and verifies the short-lived access token.
 *
 * <p>Deliberately carries no plan, quota or role claim. All three change the
 * moment an admin or a purchase changes them, and a token minted before the
 * change would authorize work the user no longer has -- so every quota check
 * reads the database, and so does every authorization check (see
 * {@link JwtAuthenticationFilter}).
 *
 * <p>Refresh tokens are not JWTs; see {@code RefreshTokenService}. They are
 * opaque and stored hashed so they can be revoked, which a self-contained JWT
 * cannot be.
 */
@Service
public class JwtService {

    private static final String CLAIM_NAME = "name";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties.Jwt config;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder, AppProperties properties) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.config = properties.jwt();
    }

    public String issueAccessToken(UserAccount user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(config.accessExpiration()))
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_NAME, user.getName())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Instant accessTokenExpiry() {
        return Instant.now().plus(config.accessExpiration());
    }

    /**
     * Verifies signature and expiry and returns the account id the token names.
     *
     * <p>Only the id. The token also carries a {@code name} claim, which is
     * handy when reading a token by hand, but it is a snapshot of one moment --
     * the filter loads the live account instead of trusting it. Returns empty
     * for anything invalid: the filter treats that as anonymous rather than as
     * an error, keeping the 401-vs-403 decision in SecurityConfig.
     */
    public Optional<Long> verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return Optional.of(Long.valueOf(jwt.getSubject()));
        } catch (JwtException | IllegalArgumentException | NullPointerException invalid) {
            return Optional.empty();
        }
    }
}
