package com.userrole.ratelimit;

import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.RateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting interceptor. Applied to all requests via RateLimitConfig.
 *
 * Key strategy:
 * - Authenticated requests: keyed by userId from SecurityContextHolder (populated by JwtFilter)
 * - Anonymous requests:     keyed by client IP from HttpServletRequest.getRemoteAddr()
 *
 * Each key gets its own independent Bucket4j bucket.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final int anonymousRequestsPerMinute;
    private final int authenticatedRequestsPerMinute;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitInterceptor(int anonymousRequestsPerMinute, int authenticatedRequestsPerMinute) {
        this.anonymousRequestsPerMinute = anonymousRequestsPerMinute;
        this.authenticatedRequestsPerMinute = authenticatedRequestsPerMinute;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        String key = resolveKey(request);
        boolean isAuthenticated = isAuthenticatedRequest();
        int limit = isAuthenticated ? authenticatedRequestsPerMinute : anonymousRequestsPerMinute;

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(limit));

        if (bucket.tryConsume(1)) {
            return true;
        }

        // Calculate approximate seconds until the bucket refills one token
        long waitSeconds = 60L / limit;
        throw new RateLimitException(waitSeconds);
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof TokenClaims claims) {
            return "user:" + claims.userId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private boolean isAuthenticatedRequest() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof TokenClaims;
    }

    private Bucket createBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
