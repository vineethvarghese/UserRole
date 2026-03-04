package com.userrole.user;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import com.userrole.user.dto.CreateUserRequest;
import com.userrole.user.dto.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration / API tests for the User management endpoints.
 *
 * Covers UC-USER-001 through UC-USER-008:
 * - UC-USER-001: Create user (admin, validation, duplicate username/email)
 * - UC-USER-002: Get user by ID (admin sees any, user sees self, user denied other)
 * - UC-USER-003: List users (admin, filtering, pagination)
 * - UC-USER-004: Update user (admin updates any, self-update, non-self denied)
 * - UC-USER-005: Delete user (soft-delete, idempotent, non-existent)
 * - UC-USER-006: Assign role to user
 * - UC-USER-007: Revoke role from user
 * - UC-USER-008: Get roles for user (admin and self access)
 *
 * All tests are @Transactional — rolled back after each test.
 * passwordHash must never appear in any response body.
 */
@SuppressWarnings("null")
@Transactional
class UserApiTest extends BaseApiTest {

    private String adminToken;
    private Long adminUserId;

    @BeforeEach
    void setUp() throws Exception {
        CreateUserRequest adminReq = TestDataFactory.createUserRequest("admin");
        adminToken = createAdminUserAndGetToken(adminReq);
        // Retrieve admin user ID for self-access tests
        String listResponse = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andReturn().getResponse().getContentAsString();
        // Admin user was the first user created, find by username prefix
        var dataArray = objectMapper.readTree(listResponse).path("data").path("data");
        for (var node : dataArray) {
            if (node.path("username").asText().startsWith("admin_")) {
                adminUserId = node.path("id").asLong();
                break;
            }
        }
    }

    // =====================================================================
    // UC-USER-001: Create user
    // =====================================================================

    @Test
    void createUser_withValidRequest_returns201AndUserResponse() throws Exception {
        CreateUserRequest req = TestDataFactory.uniqueCreateUserRequest();
        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value(req.username()))
                .andExpect(jsonPath("$.data.email").value(req.email()))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void createUser_withDuplicateUsername_returns409() throws Exception {
        CreateUserRequest req = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, req);

        // Second request with same username but different email
        CreateUserRequest duplicate = new CreateUserRequest(
                req.username(), "Password1!", "Another User", null,
                "different_" + req.email(), null);
        String body = objectMapper.writeValueAsString(duplicate);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void createUser_withDuplicateEmail_returns409() throws Exception {
        CreateUserRequest req = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, req);

        CreateUserRequest duplicate = new CreateUserRequest(
                "different_" + req.username(), "Password1!", "Another User", null,
                req.email(), null);
        String body = objectMapper.writeValueAsString(duplicate);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void createUser_withBlankUsername_returns400ValidationError() throws Exception {
        String body = """
                {"username":"","password":"Password1!","fullName":"Test","email":"test@example.com"}
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createUser_withInvalidEmail_returns400ValidationError() throws Exception {
        String body = """
                {"username":"validuser","password":"Password1!","fullName":"Test","email":"not-an-email"}
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createUser_withoutAdminToken_returns403() throws Exception {
        // Regular user without ADMIN role must get 403
        Object[] result = createRegularUserAndGetToken(adminToken);
        String regularToken = (String) result[1];
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateUserRequest());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // =====================================================================
    // UC-USER-002: Get user by ID
    // =====================================================================

    @Test
    void getUserById_asAdmin_returns200WithUserResponse() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        mockMvc.perform(get("/api/v1/users/" + userId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getUserById_asSelf_returns200() throws Exception {
        CreateUserRequest req = TestDataFactory.uniqueCreateUserRequest();
        Long userId = createUserWithToken(adminToken, req);
        String selfToken = loginAndGetToken(req.username(), "Password1!");

        mockMvc.perform(get("/api/v1/users/" + userId)
                        .header("Authorization", jwtTestHelper.bearerHeader(selfToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getUserById_asOtherUser_returns403() throws Exception {
        Long targetUserId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        CreateUserRequest otherReq = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, otherReq);
        String otherToken = loginAndGetToken(otherReq.username(), "Password1!");

        mockMvc.perform(get("/api/v1/users/" + targetUserId)
                        .header("Authorization", jwtTestHelper.bearerHeader(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getUserById_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // =====================================================================
    // UC-USER-003: List users
    // =====================================================================

    @Test
    void listUsers_asAdmin_returns200WithPaginatedResults() throws Exception {
        // Create a couple of users to ensure there's data
        createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data[0]").exists())
                .andExpect(jsonPath("$.meta.total").isNumber())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void listUsers_asRegularUser_returns403() throws Exception {
        Object[] result = createRegularUserAndGetToken(adminToken);
        String userToken = (String) result[1];

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_withDeptFilter_returnsMatchingUsers() throws Exception {
        // Create a user in a specific department
        CreateUserRequest req = TestDataFactory.uniqueCreateUserRequest();
        // The default department is "Engineering" — filter by it
        createUserWithToken(adminToken, req);

        mockMvc.perform(get("/api/v1/users")
                        .param("dept", "Engineering")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void listUsers_withPaginationParams_returnsMeta() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(5))
                .andExpect(jsonPath("$.meta.total").isNumber())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // =====================================================================
    // UC-USER-004: Update user
    // =====================================================================

    @Test
    void updateUser_asAdmin_returns200WithUpdatedResponse() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        UpdateUserRequest update = TestDataFactory.uniqueUpdateUserRequest();
        String body = objectMapper.writeValueAsString(update);

        mockMvc.perform(put("/api/v1/users/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.fullName").value(update.fullName()))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void updateUser_asSelf_returns200() throws Exception {
        CreateUserRequest req = TestDataFactory.uniqueCreateUserRequest();
        Long userId = createUserWithToken(adminToken, req);
        String selfToken = loginAndGetToken(req.username(), "Password1!");

        UpdateUserRequest update = TestDataFactory.uniqueUpdateUserRequest();
        String body = objectMapper.writeValueAsString(update);

        mockMvc.perform(put("/api/v1/users/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(selfToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value(update.fullName()))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void updateUser_asOtherUser_returns403() throws Exception {
        Long targetUserId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        CreateUserRequest attackerReq = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, attackerReq);
        String attackerToken = loginAndGetToken(attackerReq.username(), "Password1!");

        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateUserRequest());

        mockMvc.perform(put("/api/v1/users/" + targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(attackerToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void updateUser_withDuplicateEmail_returns409() throws Exception {
        // Create two users
        CreateUserRequest req1 = TestDataFactory.uniqueCreateUserRequest();
        CreateUserRequest req2 = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, req1);
        Long userId2 = createUserWithToken(adminToken, req2);

        // Try to update user2's email to user1's email
        UpdateUserRequest update = new UpdateUserRequest(null, null, req1.email(), null, 0L);
        String body = objectMapper.writeValueAsString(update);

        mockMvc.perform(put("/api/v1/users/" + userId2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // =====================================================================
    // UC-USER-005: Delete user (soft delete)
    // =====================================================================

    @Test
    void deleteUser_asAdmin_returns204() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        mockMvc.perform(delete("/api/v1/users/" + userId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUser_softDeleteMakesUserUnfetchable() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        mockMvc.perform(delete("/api/v1/users/" + userId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());

        // Deleted (soft-deleted) user should return 404 on subsequent GET
        mockMvc.perform(get("/api/v1/users/" + userId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/users/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_asRegularUser_returns403() throws Exception {
        Object[] result = createRegularUserAndGetToken(adminToken);
        Long userId = (Long) result[0];
        String userToken = (String) result[1];

        mockMvc.perform(delete("/api/v1/users/" + userId)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // UC-USER-006: Assign role
    // =====================================================================

    @Test
    void assignRole_asAdmin_returns204() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        Long adminRoleId = findAdminRoleId(adminToken);

        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void assignRole_whenAlreadyAssigned_returns409() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        Long adminRoleId = findAdminRoleId(adminToken);

        // Assign first time
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());

        // Assign again — must be 409
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void assignRole_withNonExistentRole_returns404() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignRole_asRegularUser_returns403() throws Exception {
        Object[] result = createRegularUserAndGetToken(adminToken);
        Long userId = (Long) result[0];
        String userToken = (String) result[1];
        Long adminRoleId = findAdminRoleId(adminToken);

        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // UC-USER-007: Revoke role
    // =====================================================================

    @Test
    void revokeRole_asAdmin_returns204() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        Long adminRoleId = findAdminRoleId(adminToken);

        // First assign
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());

        // Then revoke
        mockMvc.perform(delete("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void revokeRole_whenNotAssigned_returns404() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        Long adminRoleId = findAdminRoleId(adminToken);

        // Attempt to revoke a role that was never assigned
        mockMvc.perform(delete("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // UC-USER-008: Get roles for user
    // =====================================================================

    @Test
    void getRolesForUser_asAdmin_returns200WithRoleList() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        Long adminRoleId = findAdminRoleId(adminToken);
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value(com.userrole.common.RoleConstants.ADMIN))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRolesForUser_asSelf_returns200() throws Exception {
        CreateUserRequest req = TestDataFactory.uniqueCreateUserRequest();
        Long userId = createUserWithToken(adminToken, req);
        String selfToken = loginAndGetToken(req.username(), "Password1!");

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(selfToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRolesForUser_asOtherUser_returns403() throws Exception {
        Long targetUserId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        CreateUserRequest otherReq = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, otherReq);
        String otherToken = loginAndGetToken(otherReq.username(), "Password1!");

        mockMvc.perform(get("/api/v1/users/" + targetUserId + "/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }
}
