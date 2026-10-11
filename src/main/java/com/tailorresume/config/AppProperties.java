package com.tailorresume.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Bound from the {@code app.*} block of application.yml, which reads from
 * environment variables (or the gitignored {@code .env} locally).
 *
 * <p>Validated at startup: a missing or too-short JWT secret fails the boot
 * rather than silently producing forgeable tokens.
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Cors cors, Claude claude, Quota quota, Upload upload,
                            Admin admin) {

    public AppProperties {
        if (cors == null) {
            cors = new Cors(null);
        }
        if (claude == null) {
            claude = new Claude(null, null, null);
        }
        if (quota == null) {
            quota = new Quota(null, null);
        }
        if (admin == null) {
            admin = new Admin(null, null, null);
        }
        if (upload == null) {
            upload = new Upload(null);
        }
    }

    /**
     * Browser origins allowed to call the API. The Next.js dev server is the
     * only caller today; production should set CORS_ALLOWED_ORIGIN_PATTERNS.
     */
    public record Cors(List<String> allowedOriginPatterns) {

        private static final List<String> DEV_DEFAULTS = List.of(
                "http://localhost:*",
                "http://127.0.0.1:*");

        public Cors {
            if (allowedOriginPatterns == null || allowedOriginPatterns.isEmpty()) {
                allowedOriginPatterns = DEV_DEFAULTS;
            }
        }
    }

    public record Jwt(
            @NotBlank(message = "JWT_SECRET must be set") String secret,
            Duration accessExpiration,
            Duration refreshExpiration,
            String issuer) {

        /**
         * HS256 requires a key of at least 256 bits. Anything shorter is rejected
         * by Nimbus at runtime, so catch it here with a message that says what to do.
         */
        public Jwt {
            if (secret != null && Base64Secret.decodedLength(secret) < 32) {
                throw new IllegalStateException(
                        "JWT_SECRET must decode to at least 32 bytes for HS256. "
                                + "Generate one with: openssl rand -base64 48");
            }
            if (accessExpiration == null) {
                accessExpiration = Duration.ofMinutes(30);
            }
            if (refreshExpiration == null) {
                refreshExpiration = Duration.ofDays(30);
            }
            if (issuer == null || issuer.isBlank()) {
                issuer = "tailor-resume";
            }
        }
    }

    /**
     * The rewriting model. With no API key the stub client is wired in instead,
     * so the app boots and the whole test suite runs with no key and no network.
     */
    public record Claude(String apiKey, String model, Integer maxTokens) {

        public Claude {
            if (model == null || model.isBlank()) {
                model = "claude-opus-5";
            }
            // Thinking is on by default on Opus 5 and shares this ceiling with
            // the response, so leave headroom or the JSON truncates mid-object.
            if (maxTokens == null || maxTokens <= 0) {
                maxTokens = 24000;
            }
        }

        public boolean isEnabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * Monthly tailoring allowance per plan. Enforced by QuotaService; the
     * numbers live here so pricing can move without touching logic.
     */
    public record Quota(Integer freeMonthly, Integer proMonthly) {

        public Quota {
            if (freeMonthly == null || freeMonthly < 0) {
                freeMonthly = 3;
            }
            if (proMonthly == null || proMonthly < 0) {
                proMonthly = 200;
            }
        }
    }

    /** Guard rail on the uploaded resume text, independent of multipart size. */
    public record Upload(Integer maxExtractedCharacters) {

        public Upload {
            if (maxExtractedCharacters == null || maxExtractedCharacters <= 0) {
                maxExtractedCharacters = 60_000;
            }
        }
    }

    /**
     * The bootstrap administrator, created on startup when no enabled admin
     * account exists yet.
     *
     * <p>This exists because the first admin cannot be made through the admin
     * console -- you would need an admin to reach it. The alternative is a
     * hand-written UPDATE against the users table, which is exactly the kind of
     * step that gets done once, undocumented, and then cannot be repeated on a
     * fresh environment.
     *
     * <p>A blank password disables seeding entirely, which is what the test
     * profile does so that an unexpected extra account never shifts a count a
     * test is asserting on.
     */
    public record Admin(String email, String password, String name) {

        public Admin {
            if (email == null || email.isBlank()) {
                email = "admin@matchcv.ai";
            }
            if (name == null || name.isBlank()) {
                name = "MatchCV Admin";
            }
        }

        public boolean isSeedingEnabled() {
            return password != null && !password.isBlank();
        }
    }
}
