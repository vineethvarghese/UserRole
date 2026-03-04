package com.userrole.auth.spi;

import java.time.Instant;
import java.util.List;

/**
 * Provider-neutral token claims contract.
 * Implemented as a Java 21 record — immutable, no behaviour needed.
 *
 * Produced by AuthProvider.validate() and passed through the filter chain to services.
 * No code outside auth.spi should depend on how these claims were produced.
 */
public record TokenClaims(
        Long userId,
        String username,
        List<String> roles,
        Instant issuedAt,
        Instant expiresAt
) {
}
