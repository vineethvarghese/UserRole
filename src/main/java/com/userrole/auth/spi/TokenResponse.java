package com.userrole.auth.spi;

/**
 * Response returned by AuthProvider.authenticate() and AuthProvider.refresh().
 * Carries tokens and the access token expiry in seconds (for client use).
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
