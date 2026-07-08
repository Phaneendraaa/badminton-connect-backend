package com.app.badminton_backend.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Invoked by Spring Security whenever an unauthenticated request reaches a
 * protected endpoint (i.e. no valid JWT was supplied, or the token is
 * expired/invalid).
 *
 * Returns a 401 JSON body with the same shape used by GlobalExceptionHandler:
 *   { "timestamp": "...", "status": 401, "message": "..." }
 *
 * This prevents the NullPointerException / 500 that was previously produced
 * when CurrentUserService.getCurrentUser() was called with no SecurityContext.
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules(); // registers JavaTimeModule for LocalDateTime

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException, ServletException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("message", "Authentication required. Please provide a valid access token.");

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
