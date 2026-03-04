package com.userrole.support;

import com.userrole.common.RoleConstants;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Generates JWTs for use in integration tests.
 *
 * Uses the same jwt.secret from application-test.properties as the production
 * LocalAuthProvider — so all generated tokens are accepted by the running application.
 *
 * Methods:
 * - adminToken(userId)    — valid JWT with ADMIN role
 * - userToken(userId, roles) — valid JWT for a regular user
 * - expiredToken(userId)  — expired JWT (expiresAt in the past)
 * - tamperedToken(userId) — JWT signed with a wrong key (signature invalid)
 *
 * JwtTestHelper is a Spring @Component so it can be autowired by test classes that
 * need to generate tokens after creating users in the DB (so the userId is known).
 */
@Component
public class JwtTestHelper {

    private final SecretKey signingKey;
    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 900;

    public JwtTestHelper(@Value("${jwt.secret}") String jwtSecretBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecretBase64);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a valid JWT claiming the ADMIN role.
     */
    public String adminToken(Long userId, String username) {
        return buildToken(userId, username, List.of(RoleConstants.ADMIN), ACCESS_TOKEN_EXPIRY_SECONDS);
    }

    /**
     * Generates a valid JWT for a regular (non-admin) user with the specified roles.
     */
    public String userToken(Long userId, String username, List<String> roles) {
        return buildToken(userId, username, roles, ACCESS_TOKEN_EXPIRY_SECONDS);
    }

    /**
     * Generates a valid JWT for a regular user with no roles.
     */
    public String userToken(Long userId, String username) {
        return buildToken(userId, username, List.of(), ACCESS_TOKEN_EXPIRY_SECONDS);
    }

    /**
     * Generates an expired JWT (issuedAt and expiresAt both in the past).
     * The JwtFilter will reject this with HTTP 401.
     */
    public String expiredToken(Long userId, String username) {
        Instant past = Instant.now().minusSeconds(3600); // 1 hour ago
        Instant expiredAt = Instant.now().minusSeconds(1800); // 30 min ago
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", List.of())
                .issuedAt(Date.from(past))
                .expiration(Date.from(expiredAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a JWT signed with a different (wrong) key.
     * The JwtFilter will reject this with HTTP 401 due to signature mismatch.
     */
    public String tamperedToken(Long userId, String username) {
        // Use a different key — 256 bits but not the application's key
        byte[] wrongKeyBytes = Base64.getDecoder().decode(
                "d3JvbmdrZXl3cm9uZ2tleXdyb25na2V5d3JvbmdrZXk=");
        SecretKey wrongKey = Keys.hmacShaKeyFor(wrongKeyBytes);
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", List.of())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_EXPIRY_SECONDS)))
                .signWith(wrongKey)
                .compact();
    }

    /**
     * Returns a syntactically invalid JWT string (not a valid token at all).
     */
    public String malformedToken() {
        return "not.a.valid.jwt.token";
    }

    /**
     * Returns "Bearer <token>" header value for use in MockMvc requests.
     */
    public String bearerHeader(String token) {
        return "Bearer " + token;
    }

    // ---- private helpers ----

    private String buildToken(Long userId, String username, List<String> roles, long expirySeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(signingKey)
                .compact();
    }
}
