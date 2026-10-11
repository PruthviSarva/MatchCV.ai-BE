package com.tailorresume.auth;

import com.tailorresume.user.UserResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank(message = "Name is required") @Size(max = 120) String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email")
            @Size(max = 255) String email,
            // Long enough to matter, no composition rules — those push people
            // toward "Password1!" and nothing else.
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Use at least 8 characters") String password) {

        /**
         * Normalizes before validation rather than after.
         *
         * <p>Jackson builds the record through this constructor, so the trimmed
         * value is what {@code @Email} then sees. Normalizing only in the
         * service would mean an address pasted with a trailing space — which
         * phones and password managers produce constantly — is rejected with
         * "Enter a valid email" and the user cannot see what is wrong with it.
         */
        public SignupRequest {
            email = Emails.normalize(email);
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {

        public LoginRequest {
            email = Emails.normalize(email);
        }
    }

    public record RefreshRequest(@NotBlank(message = "Refresh token is required") String refreshToken) {
    }

    public record AuthResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserResponse user) {
    }
}
