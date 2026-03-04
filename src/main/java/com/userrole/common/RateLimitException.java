package com.userrole.common;

/**
 * Thrown when a caller exceeds their allocated rate limit.
 * Maps to HTTP 429 with a Retry-After header.
 */
public class RateLimitException extends UserRoleException {

    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super("RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Please retry after " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
