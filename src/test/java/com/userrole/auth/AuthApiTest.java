package com.userrole.auth;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration / API tests for authentication endpoints.
 *
 * Covers:
 * - UC-AUTH-001: Login (happy path, wrong password, unknown user, blank validation)
 * - UC-AUTH-002: Refresh token (happy path, invalid token, blank token)
 * - UC-AUTH-003: Logout (valid token returns 204)
 * - JWT validation: expired, tampered, malformed, no header, wrong prefix
 * - Public endpoint accessibility without token
 *
 * All tests are @Transactional (rollback after each). The logout test is marked
 * @Transactional at class level but works because MockMvc dispatches within the
 * test's transaction when the service methods use REQUIRED propagation.
 *
 * passwordHash must never appear in any response — asserted in every user response test.
 */
@SuppressWarnings("null")
@Transactional
class AuthApiTest extends BaseApiTest {

    private String adminToken;
    private com.userrole.user.dto.CreateUserRequest testUserReq;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminUserAndGetToken();
        testUserReq = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, testUserReq);
    }

    // =====================================================================
    // UC-AUTH-001: Login
    // =====================================================================

    @Test
    void login_withValidCredentials_returns200WithTokens() throws Exception {
        String loginBody = buildLoginBody(testUserReq.username(), "Password1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void login_withWrongPassword_returns400() throws Exception {
        String loginBody = buildLoginBody(testUserReq.username(), "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void login_withUnknownUsername_returns404() throws Exception {
        String loginBody = buildLoginBody("absolutely_nonexistent_user_abc123", "Password1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void login_withBlankUsername_returns400ValidationError() throws Exception {
        String loginBody = buildLoginBody("", "Password1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_withBlankPassword_returns400ValidationError() throws Exception {
        String loginBody = buildLoginBody("someuser", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_withMissingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // UC-AUTH-002: Refresh token
    // =====================================================================

    @Test
    void refresh_withValidRefreshToken_returns200WithNewAccessToken() throws Exception {
        // Log in to get a real refresh token
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginBody(testUserReq.username(), "Password1!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(loginResponse)
                .path("data").path("refreshToken").asText();

        String refreshBody = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void refresh_withInvalidRefreshToken_returns400() throws Exception {
        String refreshBody = """
                {"refreshToken":"this-is-not-a-valid-refresh-token-at-all"}
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void refresh_withBlankRefreshToken_returns400ValidationError() throws Exception {
        String refreshBody = """
                {"refreshToken":""}
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =====================================================================
    // UC-AUTH-003: Logout
    // =====================================================================

    @Test
    void logout_withValidBearerToken_returns204() throws Exception {
        // Use the admin token (already in context from @BeforeEach)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_withoutAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // JWT validation scenarios (11 scenarios)
    // =====================================================================

    @Test
    void protectedEndpoint_withNoAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        String expired = jwtTestHelper.expiredToken(999L, "ghost_user");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", jwtTestHelper.bearerHeader(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withTamperedSignatureToken_returns401() throws Exception {
        String tampered = jwtTestHelper.tamperedToken(999L, "ghost_user");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", jwtTestHelper.bearerHeader(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + jwtTestHelper.malformedToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withWrongBearerPrefix_returns401() throws Exception {
        // "Token" instead of "Bearer"
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Token " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withEmptyBearerValue_returns401() throws Exception {
        // "Bearer " with nothing after it — JwtFilter must not crash
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidTokenButUserHasNoAdminRole_returns403OnAdminEndpoint() throws Exception {
        // A regular user (no ADMIN role) trying to create another user → 403
        Object[] result = createRegularUserAndGetToken(adminToken);
        String userToken = (String) result[1];

        String createBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateUserRequest());
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicEndpoint_loginIsAccessibleWithoutToken() throws Exception {
        // Login endpoint must be reachable without Bearer token — is permit-all
        // Must NOT be 401 — the endpoint is public
        int status = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginBody("no_such_user_xyz", "whatever")))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(401, status,
                "Login endpoint must be accessible without a token (permit-all)");
    }

    @Test
    void publicEndpoint_refreshIsAccessibleWithoutToken() throws Exception {
        // Refresh endpoint must be reachable without Bearer token
        int status = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"fake\"}"))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(401, status,
                "Refresh endpoint must be accessible without a token (permit-all)");
    }

    @Test
    void validAdminToken_allowsAccessToAdminEndpoint() throws Exception {
        // Sanity check: valid admin token gets through the JWT filter
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateUserRequest());
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private String buildLoginBody(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }
}
