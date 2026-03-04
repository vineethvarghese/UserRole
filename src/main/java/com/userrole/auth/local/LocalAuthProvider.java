package com.userrole.auth.local;

import com.userrole.auth.spi.AuthProvider;
import com.userrole.auth.spi.TokenClaims;
import com.userrole.auth.spi.TokenResponse;
import com.userrole.auth.spi.TokenRevocationPort;
import com.userrole.common.ValidationException;
import com.userrole.user.UserService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class LocalAuthProvider implements AuthProvider, TokenRevocationPort {

    private final UserService userService;
    private final StoredTokenRepository storedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey signingKey;
    private final long accessTokenExpirySeconds;
    private final long refreshTokenExpirySeconds;

    public LocalAuthProvider(
            UserService userService,
            StoredTokenRepository storedTokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${jwt.secret}") String jwtSecretBase64,
            @Value("${jwt.access-token-expiry-seconds:900}") long accessTokenExpirySeconds,
            @Value("${jwt.refresh-token-expiry-seconds:604800}") long refreshTokenExpirySeconds
    ) {
        this.userService = userService;
        this.storedTokenRepository = storedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecretBase64);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirySeconds = accessTokenExpirySeconds;
        this.refreshTokenExpirySeconds = refreshTokenExpirySeconds;
    }

    @Override
    @Transactional
    public TokenResponse authenticate(String username, String password) {
        com.userrole.user.User user = loadUserByUsernameOrThrow(username);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ValidationException("Invalid username or password.");
        }

        // Fetch role names for JWT claims
        List<String> roleNames = userService.getRolesForUser(user.getId()).stream()
                .map(com.userrole.role.dto.RoleResponse::name)
                .toList();

        Instant now = Instant.now();
        String accessToken = buildAccessToken(user.getId(), user.getUsername(), roleNames, now);
        String refreshToken = UUID.randomUUID().toString();

        Instant refreshExpiry = now.plusSeconds(refreshTokenExpirySeconds);
        storedTokenRepository.save(new StoredToken(user.getId(), refreshToken, refreshExpiry));

        return new TokenResponse(accessToken, refreshToken, accessTokenExpirySeconds);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        StoredToken storedToken = storedTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ValidationException("Refresh token not found."));

        if (storedToken.isRevoked()) {
            throw new ValidationException("Refresh token has been revoked.");
        }
        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("Refresh token has expired.");
        }

        Long userId = storedToken.getUserId();
        com.userrole.user.User user = loadUserByIdOrThrow(userId);

        List<String> roleNames = userService.getRolesForUser(userId).stream()
                .map(com.userrole.role.dto.RoleResponse::name)
                .toList();

        Instant now = Instant.now();
        String newAccessToken = buildAccessToken(userId, user.getUsername(), roleNames, now);

        return new TokenResponse(newAccessToken, refreshToken, accessTokenExpirySeconds);
    }

    @Override
    @Transactional
    public void invalidate(String accessToken) {
        // Parse the token to extract the jti (JWT ID) which maps to the stored token
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            // Mark all tokens for this user as revoked (logout invalidates the session)
            Long userId = claims.get("userId", Long.class);
            if (userId != null) {
                storedTokenRepository.revokeAllByUserId(userId);
            }
        } catch (JwtException e) {
            // Token is already invalid — nothing to revoke, treat as success
        }
    }

    @Override
    public TokenClaims validate(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();

            return new TokenClaims(userId, username, roles, issuedAt, expiresAt);

        } catch (ExpiredJwtException e) {
            throw new ValidationException("Access token has expired.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new ValidationException("Access token is invalid.");
        }
    }

    @Override
    @Transactional
    public void revokeAllTokensForUser(Long userId) {
        storedTokenRepository.revokeAllByUserId(userId);
    }

    // ---- private helpers ----

    private String buildAccessToken(Long userId, String username, List<String> roles, Instant now) {
        Instant expiry = now.plusSeconds(accessTokenExpirySeconds);
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    private com.userrole.user.User loadUserByUsernameOrThrow(String username) {
        // auth.local -> user dependency is approved per the module dependency graph.
        // We call through the UserService interface method, not an impl class.
        return userService.findByUsername(username);
    }

    private com.userrole.user.User loadUserByIdOrThrow(Long userId) {
        return userService.findById(userId);
    }
}
