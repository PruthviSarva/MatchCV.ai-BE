package com.tailorresume.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tailorresume.common.ApiError;
import com.tailorresume.config.AppProperties;
import com.tailorresume.config.Base64Secret;
// Jackson 3 (Spring Boot 4): databind lives under tools.jackson, while the
// annotations remain under com.fasterxml.jackson.annotation.
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.List;

/**
 * Stateless JWT security. Every resume, tailoring and download is owner-scoped
 * in its service by the authenticated user id, so authorization here is simply
 * "authenticated or not" — ownership is not something a URL can assert.
 */
@Configuration
public class SecurityConfig {

    private final AppProperties properties;

    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http
                // No cookies and no sessions, so there is no CSRF vector.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Signup, login and refresh must be reachable without a token.
                        .requestMatchers("/api/auth/**").permitAll()
                        // The plan catalogue is public so pricing can be shown
                        // before signup. Subscribing is not.
                        .requestMatchers(HttpMethod.GET, "/api/billing/plans").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // The admin console is role-gated here rather than by an annotation
                        // on each method, so a new endpoint under this prefix is covered the
                        // moment it is written. The role itself is read from the database on
                        // every request, not from the token.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint(objectMapper))
                        .accessDeniedHandler(forbiddenHandler(objectMapper)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Missing or invalid token — 401, in the same JSON shape as every other error. */
    private org.springframework.security.web.AuthenticationEntryPoint unauthorizedEntryPoint(
            ObjectMapper objectMapper) {
        return (request, response, ex) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(401, "Unauthorized", "Authentication required.",
                            request.getRequestURI()));
        };
    }

    private org.springframework.security.web.access.AccessDeniedHandler forbiddenHandler(
            ObjectMapper objectMapper) {
        return (request, response, ex) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(403, "Forbidden", "You do not have access to this resource.",
                            request.getRequestURI()));
        };
    }

    /**
     * Delegating encoder: hashes new passwords with BCrypt and writes a
     * {@code {bcrypt}} prefix, so a future move to Argon2 can re-hash on login
     * without invalidating existing accounts.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private SecretKeySpec hmacKey() {
        return new SecretKeySpec(Base64Secret.decode(properties.jwt().secret()), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(hmacKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.cors().allowedOriginPatterns());
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // The download endpoints name the file in Content-Disposition; without
        // this the browser cannot read it and saves everything as "download".
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
