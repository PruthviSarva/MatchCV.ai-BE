package com.tailorresume.auth;

import com.tailorresume.auth.AuthDtos.AuthResponse;
import com.tailorresume.auth.AuthDtos.LoginRequest;
import com.tailorresume.auth.AuthDtos.SignupRequest;
import com.tailorresume.billing.SubscriptionService;
import com.tailorresume.common.ApiException;
import com.tailorresume.security.JwtService;
import com.tailorresume.user.UserAccount;
import com.tailorresume.user.UserRepository;
import com.tailorresume.user.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signup, login, refresh and logout.
 *
 * <p>Every new account gets a FREE subscription in the same transaction as the
 * user row, so there is no window in which a logged-in user has no plan and
 * every quota lookup can assume one exists.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final SubscriptionService subscriptions;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokens,
                       SubscriptionService subscriptions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.email();

        if (users.existsByEmail(email)) {
            throw ApiException.conflict("That email is already registered. Please log in.");
        }

        UserAccount user = UserAccount.create(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()));

        user.recordLogin();
        users.save(user);
        subscriptions.startFreePlan(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email();

        UserAccount user = users.findByEmail(email)
                .filter(candidate -> passwordEncoder.matches(
                        request.password(), candidate.getPasswordHash()))
                // One message for "no such account" and for "wrong password", so
                // the endpoint cannot be used to enumerate accounts.
                .orElseThrow(() -> ApiException.unauthorized("Incorrect email or password."));

        if (!user.isActive()) {
            throw ApiException.forbidden("This account is disabled.");
        }

        user.recordLogin();
        users.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String presentedToken) {
        RefreshTokenService.RotationResult rotation = refreshTokens.rotate(presentedToken);
        UserAccount user = rotation.user();

        return new AuthResponse(
                jwtService.issueAccessToken(user),
                jwtService.accessTokenExpiry(),
                rotation.token().value(),
                rotation.token().expiresAt(),
                UserResponse.from(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private AuthResponse issueTokens(UserAccount user) {
        RefreshTokenService.IssuedToken refresh = refreshTokens.issue(user);
        return new AuthResponse(
                jwtService.issueAccessToken(user),
                jwtService.accessTokenExpiry(),
                refresh.value(),
                refresh.expiresAt(),
                UserResponse.from(user));
    }

}
