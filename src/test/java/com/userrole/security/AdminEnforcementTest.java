package com.userrole.security;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security enforcement tests: every admin-only endpoint verified with:
 * (a) a regular user token → 403
 * (b) no token → 401
 *
 * Admin-only endpoints (from DESIGN.md):
 * POST   /api/v1/users                           — create user
 * GET    /api/v1/users                           — list users
 * DELETE /api/v1/users/{id}                      — delete user
 * POST   /api/v1/users/{id}/roles/{roleId}       — assign role
 * DELETE /api/v1/users/{id}/roles/{roleId}       — revoke role
 * POST   /api/v1/roles                           — create role
 * GET    /api/v1/roles                           — list roles
 * GET    /api/v1/roles/{id}                      — get role by ID
 * PUT    /api/v1/roles/{id}                      — update role
 * DELETE /api/v1/roles/{id}                      — delete role
 * POST   /api/v1/roles/{id}/permissions          — add permission
 * DELETE /api/v1/roles/{id}/permissions/{permId} — remove permission
 * GET    /api/v1/roles/{id}/permissions          — get permissions
 * POST   /api/v1/entity-types                    — create entity type
 * PUT    /api/v1/entity-types/{typeId}           — update entity type
 * DELETE /api/v1/entity-types/{typeId}           — delete entity type
 *
 * All tests are @Transactional — rolled back after each test.
 */
@SuppressWarnings("null")
@Transactional
class AdminEnforcementTest extends BaseApiTest {

    private String adminToken;
    private String regularUserToken;
    private Long existingUserId;
    private Long existingRoleId;
    private Long existingEntityTypeId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminUserAndGetToken();

        // Create a regular user with no ADMIN role
        Object[] result = createRegularUserAndGetToken(adminToken);
        existingUserId = (Long) result[0];
        regularUserToken = (String) result[1];

        // Create seeded resources for path-param tests
        existingRoleId = findAdminRoleId(adminToken);

        String etBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateEntityTypeRequest());
        String etResponse = mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(etBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        existingEntityTypeId = objectMapper.readTree(etResponse).path("data").path("id").asLong();
    }

    // =====================================================================
    // User endpoints — admin-only
    // =====================================================================

    @Test
    void createUser_withRegularUserToken_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateUserRequest());
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_withNoToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateUserRequest());
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + existingUserId)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_withNoToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + existingUserId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignRole_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + existingUserId + "/roles/" + existingRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignRole_withNoToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + existingUserId + "/roles/" + existingRoleId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeRole_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + existingUserId + "/roles/" + existingRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeRole_withNoToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + existingUserId + "/roles/" + existingRoleId))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // Role endpoints — all admin-only
    // =====================================================================

    @Test
    void createRole_withRegularUserToken_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateRoleRequest());
        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRole_withNoToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateRoleRequest());
        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRoles_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRoles_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRoleById_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/roles/" + existingRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoleById_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/roles/" + existingRoleId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateRole_withRegularUserToken_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateRoleRequest());
        mockMvc.perform(put("/api/v1/roles/" + existingRoleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_withNoToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateRoleRequest());
        mockMvc.perform(put("/api/v1/roles/" + existingRoleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteRole_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/" + existingRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRole_withNoToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/" + existingRoleId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addPermission_withRegularUserToken_returns403() throws Exception {
        String body = """
                {"action":"READ","entityTypeId":%d}
                """.formatted(existingEntityTypeId);
        mockMvc.perform(post("/api/v1/roles/" + existingRoleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addPermission_withNoToken_returns401() throws Exception {
        String body = """
                {"action":"READ","entityTypeId":%d}
                """.formatted(existingEntityTypeId);
        mockMvc.perform(post("/api/v1/roles/" + existingRoleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPermissionsForRole_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/roles/" + existingRoleId + "/permissions")
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPermissionsForRole_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/roles/" + existingRoleId + "/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removePermission_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/" + existingRoleId + "/permissions/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void removePermission_withNoToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/" + existingRoleId + "/permissions/999999"))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // Entity type endpoints — admin-only (create, update, delete)
    // =====================================================================

    @Test
    void createEntityType_withRegularUserToken_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateEntityTypeRequest());
        mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEntityType_withNoToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateEntityTypeRequest());
        mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateEntityType_withRegularUserToken_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateEntityTypeRequest());
        mockMvc.perform(put("/api/v1/entity-types/" + existingEntityTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateEntityType_withNoToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateEntityTypeRequest());
        mockMvc.perform(put("/api/v1/entity-types/" + existingEntityTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteEntityType_withRegularUserToken_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/entity-types/" + existingEntityTypeId)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteEntityType_withNoToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/entity-types/" + existingEntityTypeId))
                .andExpect(status().isUnauthorized());
    }
}
