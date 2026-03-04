package com.userrole.auth.spi;

/**
 * Port interface allowing the user module to revoke all tokens for a user
 * without directly coupling to auth.local implementation classes.
 *
 * Implemented by: com.userrole.auth.local.LocalAuthProvider
 * Consumed by: com.userrole.user.UserServiceImpl (in deleteUser soft-delete)
 */
public interface TokenRevocationPort {

    /**
     * Marks all stored tokens for the given user as revoked.
     * Called atomically within deleteUser's @Transactional boundary.
     *
     * @param userId the user whose tokens should all be revoked
     */
    void revokeAllTokensForUser(Long userId);
}
