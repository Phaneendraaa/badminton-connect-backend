package com.app.badminton_backend.auth.config;

import com.app.badminton_backend.auth.filters.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * HTTP security configuration.
 *
 * Public endpoints (no JWT required):
 *  - /auth/**              OTP login, OTP verify, refresh-token
 *  - /ws/**                WebSocket/SockJS handshake; auth is handled
 *                          separately by WebSocketAuthInterceptor on the
 *                          STOMP CONNECT frame.
 *
 * Everything else requires a valid JWT access token.  Unauthenticated
 * requests to protected endpoints are rejected by JwtAuthEntryPoint with
 * HTTP 401 and a structured JSON body — not passed through to the
 * controller where getCurrentUser() would throw a NullPointerException.
 */
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class WebSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionConfig ->
                        sessionConfig.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Public endpoints ──────────────────────────────
                        // OTP login flow and token refresh
                        .requestMatchers("/auth/**").permitAll()
                        // WebSocket/SockJS HTTP handshake — Spring Security
                        // sees these as regular HTTP requests before upgrading.
                        // Real auth happens in WebSocketAuthInterceptor on
                        // the STOMP CONNECT frame.
                        .requestMatchers("/ws/**").permitAll()
                        // ── All other endpoints require authentication ────
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(jwtAuthEntryPoint)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }
}
