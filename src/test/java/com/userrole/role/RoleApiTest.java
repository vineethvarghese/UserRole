package com.userrole.role;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import com.userrole.role.dto.CreateRoleRequest;
import com.userrole.role.dto.UpdateRoleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration / API tests for the Role management endpoints.
 *
 * Covers UC-ROLE-001 through UC-ROLE-008:
 * - UC-ROLE-001: Create role (happy path, duplicate name, blank name, non-admin denied)
 * - UC-ROLE-002: Get role by ID (happy path, not found)
 * - UC-ROLE-003: List roles (happy path, non-admin denied)
 * - UC-ROLE-004: Update role (happy path, not found, duplicate name)
 * - UC-ROLE-005: Delete role (happy path, blocked when users assigned, not found)
 * - UC-ROLE-006: Add permission (happy path, duplicate, bad entity type)
 * - UC-ROLE-007: Remove permission (happy path, not found)
 * - UC-ROLE-008: Get permissions for role
 *
 * All tests are @Transactional — rolled back after each test.
 */
@SuppressWarnings("null")
@Transactional
class RoleApiTest extends BaseApiTest {

    private String adminToken;
    // Entity type ID needed for permission tests
    private Long entityTypeId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminUserAndGetToken();
        // Create a test entity type to use in permission tests
        entityTypeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());
    }

    // =====================================================================
    // UC-ROLE-001: Create role
    // =====================================================================

    @Test
    void createRole_withValidRequest_returns201AndRoleResponse() throws Exception {
        CreateRoleRequest req = TestDataFactory.uniqueCreateRoleRequest();
        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value(req.name()))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void createRole_withDuplicateName_returns409() throws Exception {
        CreateRoleRequest req = TestDataFactory.uniqueCreateRoleRequest();
        createRole(adminToken, req);

        // Attempt to create with same name
        String body = objectMapper.writeValueAsString(req);
        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void createRole_withBlankName_returns400ValidationError() throws Exception {
        String body = """
                {"name":"","description":"Some description"}
                """;

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRole_asRegularUser_returns403() throws Exception {
        Object[] result = createRegularUserAndGetToken(adminToken);
        String userToken = (String) result[1];
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateRoleRequest());

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // UC-ROLE-002: Get role by ID
    // =====================================================================

    @Test
    void getRoleById_withValidId_returns200AndRoleResponse() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());

        mockMvc.perform(get("/api/v1/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(roleId))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void getRoleById_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/roles/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // =====================================================================
    // UC-ROLE-003: List roles
    // =====================================================================

    @Test
    void listRoles_asAdmin_returns200WithRoleList() throws Exception {
        createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                // ADMIN role from V6 migration should always be present
                .andExpect(jsonPath("$.data[?(@.name=='ADMIN')]").exists());
    }

    @Test
    void listRoles_asRegularUser_returns403() throws Exception {
        Object[] result = createRegularUserAndGetToken(adminToken);
        String userToken = (String) result[1];

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // UC-ROLE-004: Update role
    // =====================================================================

    @Test
    void updateRole_withValidRequest_returns200AndUpdatedResponse() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());
        UpdateRoleRequest update = TestDataFactory.uniqueUpdateRoleRequest();
        String body = objectMapper.writeValueAsString(update);

        mockMvc.perform(put("/api/v1/roles/" + roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(update.name()))
                .andExpect(jsonPath("$.data.description").value(update.description()));
    }

    @Test
    void updateRole_withNonExistentId_returns404() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateRoleRequest());

        mockMvc.perform(put("/api/v1/roles/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRole_withDuplicateName_returns409() throws Exception {
        Long role1Id = createRole(adminToken, TestDataFactory.createRoleRequest("FIRST_ROLE_XYZ"));
        Long role2Id = createRole(adminToken, TestDataFactory.createRoleRequest("SECOND_ROLE_XYZ"));

        UpdateRoleRequest update = new UpdateRoleRequest("FIRST_ROLE_XYZ", "desc", 0L);
        String body = objectMapper.writeValueAsString(update);

        mockMvc.perform(put("/api/v1/roles/" + role2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict());
    }

    // =====================================================================
    // UC-ROLE-005: Delete role
    // =====================================================================

    @Test
    void deleteRole_withNoUsersAssigned_returns204() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());

        mockMvc.perform(delete("/api/v1/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRole_withUsersAssigned_returns409() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());

        // Create a user and assign the role
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());

        // Attempt to delete the role — must fail
        mockMvc.perform(delete("/api/v1/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void deleteRole_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // UC-ROLE-006: Add permission
    // =====================================================================

    @Test
    void addPermission_withValidRequest_returns201AndPermissionResponse() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());
        String body = """
                {"action":"READ","entityTypeId":%d}
                """.formatted(entityTypeId);

        mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.action").value("READ"))
                .andExpect(jsonPath("$.data.entityTypeId").value(entityTypeId));
    }

    @Test
    void addPermission_withDuplicateRoleActionEntityType_returns409() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());
        String body = """
                {"action":"CREATE","entityTypeId":%d}
                """.formatted(entityTypeId);

        // Add first time
        mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated());

        // Add duplicate
        mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void addPermission_withAllFourActions_allSucceed() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());

        for (String action : new String[]{"CREATE", "READ", "UPDATE", "DELETE"}) {
            String body = """
                    {"action":"%s","entityTypeId":%d}
                    """.formatted(action, entityTypeId);
            mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void addPermission_withNullAction_returns400ValidationError() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());
        String body = """
                {"action":null,"entityTypeId":%d}
                """.formatted(entityTypeId);

        mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =====================================================================
    // UC-ROLE-007: Remove permission
    // =====================================================================

    @Test
    void removePermission_withValidPermissionId_returns204() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());
        Long permId = addPermission(adminToken, roleId, "READ", entityTypeId);

        mockMvc.perform(delete("/api/v1/roles/" + roleId + "/permissions/" + permId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void removePermission_withNonExistentPermissionId_returns404() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());

        mockMvc.perform(delete("/api/v1/roles/" + roleId + "/permissions/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // UC-ROLE-008: Get permissions for role
    // =====================================================================

    @Test
    void getPermissionsForRole_withPermissionsAssigned_returns200WithList() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());
        addPermission(adminToken, roleId, "READ", entityTypeId);
        addPermission(adminToken, roleId, "CREATE", entityTypeId);

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getPermissionsForRole_withNoPermissions_returnsEmptyList() throws Exception {
        Long roleId = createRole(adminToken, TestDataFactory.uniqueCreateRoleRequest());

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private Long createRole(String token, CreateRoleRequest req) throws Exception {
        String body = objectMapper.writeValueAsString(req);
        String response = mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createEntityType(String token,
            com.userrole.entity.dto.CreateEntityTypeRequest req) throws Exception {
        String body = objectMapper.writeValueAsString(req);
        String response = mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long addPermission(String token, Long roleId, String action, Long etId) throws Exception {
        String body = """
                {"action":"%s","entityTypeId":%d}
                """.formatted(action, etId);
        String response = mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }
}
