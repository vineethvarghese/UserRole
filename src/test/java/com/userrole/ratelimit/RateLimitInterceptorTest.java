package com.userrole.ratelimit;

import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandle_whenAnonymousRequestWithinLimit_returnsTrue() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        // Limit of 5 per minute — first request should pass
        RateLimitInterceptor interceptor = new RateLimitInterceptor(5, 10);

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
    }

    @Test
    void preHandle_whenAnonymousRequestExceedsLimit_throwsRateLimitException() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        RateLimitInterceptor interceptor = new RateLimitInterceptor(3, 10);

        // Exhaust the bucket
        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);

        // 4th request should be rate-limited
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void preHandle_whenAuthenticatedRequestWithinLimit_returnsTrue() throws Exception {
        setAuthenticatedUser(1L, "alice");
        RateLimitInterceptor interceptor = new RateLimitInterceptor(5, 10);

        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
    }

    @Test
    void preHandle_whenAuthenticatedRequestExceedsLimit_throwsRateLimitException() throws Exception {
        setAuthenticatedUser(2L, "bob");
        RateLimitInterceptor interceptor = new RateLimitInterceptor(5, 3);

        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void preHandle_whenTwoDifferentIpsHaveIndependentBuckets_bothPassWithinLimit() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(3, 10);

        // IP 1 exhausts its bucket
        when(request.getRemoteAddr()).thenReturn("1.1.1.1");
        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);

        // IP 2 should still have its own full bucket
        when(request.getRemoteAddr()).thenReturn("2.2.2.2");
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
    }

    @Test
    void preHandle_whenTwoDifferentUserIdsHaveIndependentBuckets_bothPassWithinLimit() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(5, 3);

        // User 1 exhausts its bucket
        setAuthenticatedUser(1L, "user1");
        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);

        // User 2 should have its own full bucket
        setAuthenticatedUser(2L, "user2");
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
    }

    private void setAuthenticatedUser(Long userId, String username) {
        TokenClaims claims = new TokenClaims(userId, username, List.of("EDITOR"),
                Instant.now(), Instant.now().plusSeconds(900));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
