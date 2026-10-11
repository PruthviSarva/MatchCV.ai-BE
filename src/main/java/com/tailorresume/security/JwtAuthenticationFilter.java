package com.tailorresume.security;

import com.tailorresume.user.UserAccount;
import com.tailorresume.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <token>} and populates the security context.
 *
 * <p>An absent or invalid token is left anonymous rather than rejected here --
 * the filter chain's authorization rules decide whether that is allowed.
 *
 * <p>A verified token is only half the check. The account's role and its active
 * flag are read from the database on every request rather than from claims,
 * which costs one indexed primary-key lookup and buys the thing that makes an
 * admin console safe to operate: when an admin disables an account or drops its
 * role, the next request from that session already fails. Signing those facts
 * into the token would leave a disabled user fully authorized for the remainder
 * of the access token's lifetime, which is the whole half hour.
 *
 * <p>This is the same reasoning that keeps plan and quota out of the token --
 * see {@link JwtService}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            jwtService.verify(token)
                    .flatMap(users::findById)
                    // A deleted or disabled account stays anonymous, which the
                    // chain then turns into a 401 rather than a 403 -- the
                    // session is over, not merely short of a permission.
                    .filter(UserAccount::isActive)
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(UserAccount user) {
        var principal = new AuthenticatedUser(user.getId(), user.getName(), user.getRole());
        var authorities = List.of(new SimpleGrantedAuthority(user.getRole().authority()));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
