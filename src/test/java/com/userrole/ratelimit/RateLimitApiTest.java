package com.userrole.ratelimit;

import com.userrole.support.JwtTestHelper;
import com.userrole.support.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rate limiting integration tests (RATE series from QE_TEST_PLAN.md §9.10).
 *
 * ISOLATION RULES (QE_TEST_PLAN.md §3, §6):
 * - This class does NOT extend BaseApiTest — it needs its own isolated Spring context
 *   because @TestPropertySource sets different rate-limit values (3 anon, 5 auth)
 *   that must not bleed into the shared context used by all other test classes.
 * - @DirtiesContext(classMode = AFTER_CLASS) discards this context after the class
 *   finishes so the modified context does not persist for subsequent tests.
 * - @SpringBootTest and @AutoConfigureMockMvc are declared directly on this class.
 * - @ActiveProfiles("test") activates application-test.properties.
 *
 * BUCKET ISOLATION STRATEGY (QE_TEST_PLAN.md §6.4):
 * - Each test that exhausts a bucket uses a unique remote-address (X-Forwarded-For
 *   header) so buckets never collide across test methods within the class.
 *   The RateLimitInterceptor keys anonymous requests by HttpServletRequest.getRemoteAddr().
 *   MockMvc sends requests with remoteAddr "127.0.0.1" by default, so we distinguish
 *   tests by building requests with distinct addresses via MockMvcRequestBuilders
 *   builder that accepts a custom remote address where possible, or by ensuring
 *   each test uses a unique authenticated userId so the bucket key is different.
 *
 * RATE LIMIT VALUES UNDER TEST:
 *   anonymous.requests-per-minute  = 3  → send 4 to trigger 429
 *   authenticated.requests-per-minute = 5  → send 6 to trigger 429
 *
 * Scenarios:
 *   RATE-001  Anonymous (limit+1) → 429
 *   RATE-002  429 body conforms to error envelope
 *   RATE-003  429 includes Retry-After header with positive integer
 *   RATE-004  Two anonymous callers from different IPs — buckets independent
 *   RATE-005  Authenticated (auth limit+1) → 429
 *   RATE-006  Two authenticated users from same IP — buckets independent (userId-keyed)
 *   RATE-007  After 429, next request from same key → still 429
 */
@SuppressWarnings("null")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "ratelimit.anonymous.requests-per-minute=3",
        "ratelimit.authenticated.requests-per-minute=5"
})
class RateLimitApiTest {

    // The anonymous limit is 3; auth limit is 5
    private static final int ANON_LIMIT  = 3;
    private static final int AUTH_LIMIT  = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTestHelper jwtTestHelper;

    // =====================================================================
    // RATE-001: Anonymous caller — (limit+1)th request returns 429
    // =====================================================================
    @Test
    void anonymousCaller_exceedsLimit_returns429() throws Exception {
        // Login endpoint is permit-all and is in /api/v1/** → interceptor applies
        // Use the public login endpoint so no auth is needed
        for (int i = 0; i < ANON_LIMIT; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                            .with(req -> { req.setRemoteAddr("10.0.1.1"); return req; }))
                    .andExpect(status().is(not(429)));
        }
        // (limit+1)th request — must be 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                        .with(req -> { req.setRemoteAddr("10.0.1.1"); return req; }))
                .andExpect(status().isTooManyRequests());
    }

    // =====================================================================
    // RATE-002: 429 body conforms to ADR-0009 error envelope
    // =====================================================================
    @Test
    void rateLimitExceeded_responseBodyConformsToErrorEnvelope() throws Exception {
        for (int i = 0; i < ANON_LIMIT; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                            .with(req -> { req.setRemoteAddr("10.0.2.1"); return req; }))
                    .andExpect(status().is(not(429)));
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                        .with(req -> { req.setRemoteAddr("10.0.2.1"); return req; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // =====================================================================
    // RATE-003: 429 response includes Retry-After header with positive integer
    // =====================================================================
    @Test
    void rateLimitExceeded_retryAfterHeaderIsPresent() throws Exception {
        for (int i = 0; i < ANON_LIMIT; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                            .with(req -> { req.setRemoteAddr("10.0.3.1"); return req; }))
                    .andExpect(status().is(not(429)));
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                        .with(req -> { req.setRemoteAddr("10.0.3.1"); return req; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Retry-After", matchesPattern("\\d+")));
    }

    // =====================================================================
    // RATE-004: Two anonymous callers from different IPs — independent buckets
    // =====================================================================
    @Test
    void twoDifferentAnonymousIps_haveSeparateBuckets_neitherHits429AtLimit() throws Exception {
        String ip1 = "10.0.4.1";
        String ip2 = "10.0.4.2";

        // Each IP sends exactly ANON_LIMIT requests — neither should hit 429
        for (int i = 0; i < ANON_LIMIT; i++) {
            final int copy = i;
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                            .with(req -> { req.setRemoteAddr(ip1); return req; }))
                    .andExpect(status().is(not(429)));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                            .with(req -> { req.setRemoteAddr(ip2); return req; }))
                    .andExpect(status().is(not(429)));
        }
    }

    // =====================================================================
    // RATE-005: Authenticated caller — (auth limit+1)th request returns 429
    // =====================================================================
    @Test
    @Transactional
    void authenticatedCaller_exceedsAuthLimit_returns429() throws Exception {
        // Use a synthetic JWT (valid signature, userId=5001 which does not exist in DB)
        // for the rate-limit endpoint hit. The RateLimitInterceptor fires BEFORE the
        // service layer checks if the user exists — it only needs a valid TokenClaims
        // principal in the SecurityContext (populated by JwtFilter).
        // We use GET /api/v1/roles because it requires auth but has no body.
        String token = jwtTestHelper.adminToken(5001L, "ratelimit_user_auth");

        for (int i = 0; i < AUTH_LIMIT; i++) {
            mockMvc.perform(get("/api/v1/roles")
                            .header("Authorization", jwtTestHelper.bearerHeader(token)))
                    .andExpect(status().is(not(429)));
        }
        // (limit+1)th request — must be 429
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(token)))
                .andExpect(status().isTooManyRequests());
    }

    // =====================================================================
    // RATE-006: Two authenticated users from same IP — independent (userId-keyed) buckets
    // =====================================================================
    @Test
    void twoAuthenticatedUsersFromSameIp_haveSeparateBuckets() throws Exception {
        // Two synthetic tokens with different userIds — both from the default IP
        String tokenA = jwtTestHelper.adminToken(6001L, "ratelimit_userA");
        String tokenB = jwtTestHelper.adminToken(6002L, "ratelimit_userB");

        // Each user sends exactly AUTH_LIMIT requests — neither should hit 429
        for (int i = 0; i < AUTH_LIMIT; i++) {
            mockMvc.perform(get("/api/v1/roles")
                            .header("Authorization", jwtTestHelper.bearerHeader(tokenA)))
                    .andExpect(status().is(not(429)));

            mockMvc.perform(get("/api/v1/roles")
                            .header("Authorization", jwtTestHelper.bearerHeader(tokenB)))
                    .andExpect(status().is(not(429)));
        }
        // User A's (AUTH_LIMIT+1)th request — should be 429
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(tokenA)))
                .andExpect(status().isTooManyRequests());

        // User B has also hit AUTH_LIMIT — their next request should also be 429
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(tokenB)))
                .andExpect(status().isTooManyRequests());
    }

    // =====================================================================
    // RATE-007: After 429, subsequent request from same key → still 429
    // =====================================================================
    @Test
    void afterReceiving429_subsequentRequestFromSameKey_alsoReturns429() throws Exception {
        String ip = "10.0.7.1";
        // Exhaust the bucket
        for (int i = 0; i < ANON_LIMIT; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                            .with(req -> { req.setRemoteAddr(ip); return req; }))
                    .andExpect(status().is(not(429)));
        }
        // First 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                        .with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isTooManyRequests());

        // Second request — bucket still exhausted, should still be 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nouser\",\"password\":\"nopass\"}")
                        .with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isTooManyRequests());
    }
}
