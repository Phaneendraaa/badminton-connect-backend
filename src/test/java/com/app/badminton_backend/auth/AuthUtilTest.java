package com.app.badminton_backend.auth;

import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.entity.type.UserRole;
import com.app.badminton_backend.auth.util.AuthUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthUtilTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";

    private AuthUtil authUtil;
    private User user;

    @BeforeEach
    void setUp() {
        authUtil = new AuthUtil();
        ReflectionTestUtils.setField(authUtil, "jwtSecretKey", SECRET);
        user = User.builder()
                .id(UUID.randomUUID())
                .phoneNumber("+919876543210")
                .role(UserRole.USER)
                .build();
    }

    @Test
    void accessToken_roundTripsUserIdAndPhoneNumber() {
        String token = authUtil.generateAccessToken(user);

        assertEquals(user.getId().toString(), authUtil.getUserIdFromToken(token));
        assertEquals("+919876543210", authUtil.getPhoneNumberFromToken(token));
    }

    @Test
    void accessToken_isValid_andExpiresInAboutOneDay() {
        String token = authUtil.generateAccessToken(user);

        assertTrue(authUtil.isTokenValid(token));

        Date expiration = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        long msUntilExpiry = expiration.getTime() - System.currentTimeMillis();
        assertTrue(msUntilExpiry > 86_400_000L - 60_000L, "expiry should be ~24h away");
        assertTrue(msUntilExpiry <= 86_400_000L, "expiry should not exceed 24h");
    }

    @Test
    void isTokenValid_rejectsTokenSignedWithDifferentKey() {
        AuthUtil other = new AuthUtil();
        ReflectionTestUtils.setField(other, "jwtSecretKey", "a-completely-different-secret-key-32-bytes-min");
        String foreignToken = other.generateAccessToken(user);

        assertFalse(authUtil.isTokenValid(foreignToken));
    }

    @Test
    void isTokenValid_rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(authUtil.isTokenValid(expired));
    }

    @Test
    void isTokenValid_rejectsGarbageAndBlankInput() {
        assertFalse(authUtil.isTokenValid("not.a.jwt"));
        assertFalse(authUtil.isTokenValid(""));
    }

    @Test
    void refreshToken_isUrlSafe_longEnough_andUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = authUtil.generateRefreshToken();
            assertTrue(token.matches("^[A-Za-z0-9_-]+$"), "token must be URL-safe base64 without padding");
            assertEquals(86, token.length(), "64 random bytes encode to 86 base64url chars");
            seen.add(token);
        }
        assertEquals(200, seen.size(), "refresh tokens must not repeat");
    }
}
