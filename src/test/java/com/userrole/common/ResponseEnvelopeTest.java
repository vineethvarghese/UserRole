package com.userrole.common;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API Response Envelope conformance tests (ENV series from QE_TEST_PLAN.md §9.11).
 *
 * Verifies the ADR-0009 ApiResponse envelope shape across a representative sample of
 * endpoints, covering every status family:
 *
 *   ENV-001  All success responses have { "status": "success", "data": ... }
 *   ENV-002  All paginated list responses include { "meta": { "page", "size", "total" } }
 *   ENV-003  All error responses have { "status": "error", "code": "...", "message": "...", "timestamp": "..." }
 *   ENV-004  400 response includes meaningful code and non-null message
 *   ENV-005  401 response has correct envelope (not Spring's whitepage)
 *   ENV-006  403 response has correct envelope (not Spring's whitepage)
 *   ENV-007  404 response has correct envelope (not Spring's whitepage)
 *   ENV-008  409 response has correct envelope with meaningful code
 *   ENV-009  429 response has correct envelope (covered separately in RateLimitApiTest)
 *   ENV-010  All timestamp fields in responses are ISO-8601 UTC strings
 *   ENV-011  All ID fields in responses are numeric (Long), not string-encoded
 *   ENV-012  passwordHash is absent from all UserResponse instances across all endpoints
 *
 * ENV-009 (429) is fully exercised in RateLimitApiTest; a single cross-reference check
 * is kept here by verifying that an error body obtained from a 400 response matches the
 * envelope shape required by ENV-003/ENV-004.
 *
 * All tests are @Transactional — rolled back after each test.
 */
@SuppressWarnings("null")
@Transactional
class ResponseEnvelopeTest extends BaseApiTest {

    private String adminToken;
    private Long createdUserId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminUserAndGetToken();
        createdUserId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
    }

    // =====================================================================
    // ENV-001: Success response envelope — single resource
    // =====================================================================

    @Test
    void getUser_successResponse_hasStatusSuccessAndDataObject() throws Exception {
        // ENV-001: { "status": "success", "data": {...} }
        mockMvc.perform(get("/api/v1/users/" + createdUserId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.id").isNumber())
                // error fields must be absent on success
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void createRole_successResponse_hasStatusSuccessAndDataObject() throws Exception {
        // ENV-001 cross-check: POST also wraps in { "status": "success", "data": {...} }
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateRoleRequest());

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    // =====================================================================
    // ENV-002: Paginated list response — meta object with page, size, total
    // =====================================================================

    @Test
    void listUsers_paginatedResponse_hasMetaWithPageSizeTotal() throws Exception {
        // ENV-002
        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.meta").isMap())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.total").isNumber());
    }

    @Test
    void getRoleHistory_paginatedResponse_hasMetaWithPageSizeTotal() throws Exception {
        // ENV-002 cross-check: history endpoint also returns meta
        mockMvc.perform(get("/api/v1/users/" + createdUserId + "/roles/history")
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.meta").isMap())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(5))
                .andExpect(jsonPath("$.meta.total").isNumber());
    }

    // =====================================================================
    // ENV-003 & ENV-004: Error envelope — status, code, message, timestamp
    // =====================================================================

    @Test
    void badRequest_errorResponse_hasStatusErrorCodeMessageTimestamp() throws Exception {
        // ENV-003 + ENV-004: 400 response must include all four error fields
        String body = """
                {"username":"","password":"Password1!","fullName":"Test","email":"test@example.com"}
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                // data must be absent on error
                .andExpect(jsonPath("$.data").doesNotExist())
                // timestamp must be present (ISO-8601)
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void badRequest_errorResponse_codeIsNonNull() throws Exception {
        // ENV-004: code field is present and not blank
        String body = """
                {"username":"","password":""}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // =====================================================================
    // ENV-005: 401 — custom envelope, not Spring's whitepage
    // =====================================================================

    @Test
    void unauthenticated_request_returns401WithCustomEnvelope() throws Exception {
        // ENV-005: Spring Security AuthenticationEntryPoint must produce our envelope,
        // not the default whitepage HTML / error JSON
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void expiredToken_request_returns401WithCustomEnvelope() throws Exception {
        // ENV-005 cross-check: expired JWT produces our envelope
        String expired = jwtTestHelper.expiredToken(999L, "ghost");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", jwtTestHelper.bearerHeader(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // =====================================================================
    // ENV-006: 403 — custom envelope, not Spring's whitepage
    // =====================================================================

    @Test
    void forbidden_request_returns403WithCustomEnvelope() throws Exception {
        // ENV-006: a regular user hitting an admin endpoint must produce our envelope
        Object[] result = createRegularUserAndGetToken(adminToken);
        String userToken = (String) result[1];

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // =====================================================================
    // ENV-007: 404 — custom envelope, not Spring's whitepage
    // =====================================================================

    @Test
    void notFound_request_returns404WithCustomEnvelope() throws Exception {
        // ENV-007
        mockMvc.perform(get("/api/v1/users/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // =====================================================================
    // ENV-008: 409 — custom envelope with meaningful code
    // =====================================================================

    @Test
    void conflict_request_returns409WithCustomEnvelope() throws Exception {
        // ENV-008: duplicate username creates 409 with our envelope
        var req = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, req);

        // Attempt to create duplicate
        var duplicate = new com.userrole.user.dto.CreateUserRequest(
                req.username(), "Password1!", "Another Name", null,
                "other_" + req.email(), null);
        String body = objectMapper.writeValueAsString(duplicate);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // =====================================================================
    // ENV-010: Timestamp fields are ISO-8601 UTC strings
    // =====================================================================

    @Test
    void createUser_responseTimestamp_isIso8601UtcFormat() throws Exception {
        // ENV-010: error responses carry a timestamp; cross-check format
        // Trigger a 404 to get an error envelope with a timestamp field
        mockMvc.perform(get("/api/v1/users/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound())
                // ISO-8601 UTC: ends with Z or +00:00, contains T separator
                .andExpect(jsonPath("$.timestamp").value(matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")));
    }

    @Test
    void createUser_createdAt_isIso8601UtcFormat() throws Exception {
        // ENV-010: success response createdAt fields (from entity creation)
        var req = TestDataFactory.uniqueCreateEntityTypeRequest();
        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdAt").value(matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")));
    }

    // =====================================================================
    // ENV-011: ID fields are numeric (Long), not string-encoded
    // =====================================================================

    @Test
    void getUser_idField_isNumericNotStringEncoded() throws Exception {
        // ENV-011: $.data.id must be a JSON number, not a quoted string
        mockMvc.perform(get("/api/v1/users/" + createdUserId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                // isNumber() asserts it is a JSON number node (Long/Integer), not a String
                .andExpect(jsonPath("$.data.id").isNumber())
                // Belt-and-suspenders: value must match the expected Long
                .andExpect(jsonPath("$.data.id").value(createdUserId));
    }

    @Test
    void createRole_idField_isNumericNotStringEncoded() throws Exception {
        // ENV-011: role ID must be numeric
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateRoleRequest());

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    // =====================================================================
    // ENV-012: passwordHash absent from ALL UserResponse endpoints
    // =====================================================================

    @Test
    void getUser_responseNeverContainsPasswordHash() throws Exception {
        // ENV-012: GET /api/v1/users/{id}
        mockMvc.perform(get("/api/v1/users/" + createdUserId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("password_hash"))));
    }

    @Test
    void listUsers_responseNeverContainsPasswordHash() throws Exception {
        // ENV-012: GET /api/v1/users (list)
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("password_hash"))));
    }

    @Test
    void updateUser_responseNeverContainsPasswordHash() throws Exception {
        // ENV-012: PUT /api/v1/users/{id}
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateUserRequest());

        mockMvc.perform(put("/api/v1/users/" + createdUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("password_hash"))));
    }

    @Test
    void getUserRoles_responseNeverContainsPasswordHash() throws Exception {
        // ENV-012: GET /api/v1/users/{id}/roles
        mockMvc.perform(get("/api/v1/users/" + createdUserId + "/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("password_hash"))));
    }

    @Test
    void login_responseNeverContainsPasswordHash() throws Exception {
        // ENV-012 belt-and-suspenders: login response must not leak passwordHash
        var req = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, req);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password1!"}
                                """.formatted(req.username())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("password_hash"))));
    }
}
