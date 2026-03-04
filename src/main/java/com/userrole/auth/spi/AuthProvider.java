package com.userrole.auth.spi;

/**
 * Authentication provider SPI (Service Provider Interface).
 * All callers outside the auth package interact with auth exclusively through this interface.
 *
 * v1 implementation: LocalAuthProvider (DB credentials + local JWT signing)
 * Future: ExternalAuthProvider (Keycloak, Auth0, etc.) — zero changes outside auth package
 */
public interface AuthProvider {

    /**
     * Authenticates a user by username and password.
     * Returns a TokenResponse containing an access token, refresh token, and expiry.
     * Throws an exception if credentials are invalid.
     */
    TokenResponse authenticate(String username, String password);

    /**
     * Exchanges a valid refresh token for a new access token.
     * Throws an exception if the refresh token is expired, revoked, or not found.
     */
    TokenResponse refresh(String refreshToken);

    /**
     * Invalidates the given access token (server-side revocation).
     * Subsequent validate() calls for this token will throw an exception.
     */
    void invalidate(String accessToken);

    /**
     * Validates an access token and returns its claims.
     * Throws an exception if the token is invalid, expired, or revoked.
     */
    TokenClaims validate(String accessToken);
}
