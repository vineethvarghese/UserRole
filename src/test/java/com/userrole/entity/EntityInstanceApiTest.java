package com.userrole.entity;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import com.userrole.entity.dto.CreateEntityTypeRequest;
import com.userrole.user.dto.CreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration / API tests for the Entity Instance endpoints.
 *
 * Covers UC-ENT-006 through UC-ENT-010:
 * - UC-ENT-006: Create entity instance (with CREATE permission, without permission → 403)
 * - UC-ENT-007: Get entity instance (with READ permission, without → 403, not found → 404)
 * - UC-ENT-008: List entity instances (with READ permission, without → 403)
 * - UC-ENT-009: Update entity instance (with UPDATE permission, without → 403, not found → 404)
 * - UC-ENT-010: Delete entity instance (with DELETE permission, without → 403)
 *
 * Setup:
 * - Creates an admin user, an entity type, and a role with all permissions on that type.
 * - Creates an authorised user (with the role) and an unauthorised user (no role).
 *
 * All tests are @Transactional — rolled back after each test.
 */
@SuppressWarnings("null")
@Transactional
class EntityInstanceApiTest extends BaseApiTest {

    private String adminToken;
    private Long entityTypeId;
    private String authorisedUserToken;
    private String unauthorisedUserToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminUserAndGetToken();

        // Create entity type
        entityTypeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());

        // Create a role with all four permissions on the entity type
        Long roleId = createRole(adminToken);
        for (String action : new String[]{"CREATE", "READ", "UPDATE", "DELETE"}) {
            addPermission(adminToken, roleId, action, entityTypeId);
        }

        // Authorised user — has the role
        CreateUserRequest authReq = TestDataFactory.createUserRequest("authorised");
        Long authUserId = createUserWithToken(adminToken, authReq);
        mockMvc.perform(post("/api/v1/users/" + authUserId + "/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
        authorisedUserToken = loginAndGetToken(authReq.username(), "Password1!");

        // Unauthorised user — no role assigned
        CreateUserRequest unauthReq = TestDataFactory.createUserRequest("unauthorised");
        createUserWithToken(adminToken, unauthReq);
        unauthorisedUserToken = loginAndGetToken(unauthReq.username(), "Password1!");
    }

    // =====================================================================
    // UC-ENT-006: Create entity instance
    // =====================================================================

    @Test
    void createInstance_withCreatePermission_returns201AndInstanceResponse() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());

        mockMvc.perform(post("/api/v1/entity-types/" + entityTypeId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.entityTypeId").value(entityTypeId))
                .andExpect(jsonPath("$.data.createdBy").isNumber())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void createInstance_withoutCreatePermission_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());

        mockMvc.perform(post("/api/v1/entity-types/" + entityTypeId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(unauthorisedUserToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void createInstance_withoutToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());

        mockMvc.perform(post("/api/v1/entity-types/" + entityTypeId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInstance_forNonExistentEntityType_returns404() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());

        mockMvc.perform(post("/api/v1/entity-types/999999/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createInstance_withBlankName_returns400ValidationError() throws Exception {
        String body = """
                {"name":"","description":"some description"}
                """;

        mockMvc.perform(post("/api/v1/entity-types/" + entityTypeId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =====================================================================
    // UC-ENT-007: Get entity instance by ID
    // =====================================================================

    @Test
    void getInstanceById_withReadPermission_returns200AndInstanceResponse() throws Exception {
        Long instanceId = createInstance(authorisedUserToken);

        mockMvc.perform(get("/api/v1/entity-types/" + entityTypeId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(instanceId))
                .andExpect(jsonPath("$.data.entityTypeId").value(entityTypeId));
    }

    @Test
    void getInstanceById_withoutReadPermission_returns403() throws Exception {
        Long instanceId = createInstance(authorisedUserToken);

        mockMvc.perform(get("/api/v1/entity-types/" + entityTypeId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(unauthorisedUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInstanceById_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/entity-types/" + entityTypeId + "/instances/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // UC-ENT-008: List entity instances
    // =====================================================================

    @Test
    void listInstances_withReadPermission_returns200WithPaginatedResults() throws Exception {
        createInstance(authorisedUserToken);
        createInstance(authorisedUserToken);

        mockMvc.perform(get("/api/v1/entity-types/" + entityTypeId + "/instances")
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.meta.total").isNumber());
    }

    @Test
    void listInstances_withoutReadPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/entity-types/" + entityTypeId + "/instances")
                        .header("Authorization", jwtTestHelper.bearerHeader(unauthorisedUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listInstances_withPaginationParams_returnsMeta() throws Exception {
        mockMvc.perform(get("/api/v1/entity-types/" + entityTypeId + "/instances")
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(5))
                .andExpect(jsonPath("$.meta.total").isNumber());
    }

    // =====================================================================
    // UC-ENT-009: Update entity instance
    // =====================================================================

    @Test
    void updateInstance_withUpdatePermission_returns200AndUpdatedResponse() throws Exception {
        Long instanceId = createInstance(authorisedUserToken);
        var update = TestDataFactory.uniqueUpdateInstanceRequest();
        String body = objectMapper.writeValueAsString(update);

        mockMvc.perform(put("/api/v1/entity-types/" + entityTypeId + "/instances/" + instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(update.name()))
                .andExpect(jsonPath("$.data.description").value(update.description()));
    }

    @Test
    void updateInstance_withoutUpdatePermission_returns403() throws Exception {
        Long instanceId = createInstance(authorisedUserToken);
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateInstanceRequest());

        mockMvc.perform(put("/api/v1/entity-types/" + entityTypeId + "/instances/" + instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(unauthorisedUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateInstance_withNonExistentId_returns404() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateInstanceRequest());

        mockMvc.perform(put("/api/v1/entity-types/" + entityTypeId + "/instances/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // UC-ENT-010: Delete entity instance
    // =====================================================================

    @Test
    void deleteInstance_withDeletePermission_returns204() throws Exception {
        Long instanceId = createInstance(authorisedUserToken);

        mockMvc.perform(delete("/api/v1/entity-types/" + entityTypeId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteInstance_withoutDeletePermission_returns403() throws Exception {
        Long instanceId = createInstance(authorisedUserToken);

        mockMvc.perform(delete("/api/v1/entity-types/" + entityTypeId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(unauthorisedUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteInstance_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/entity-types/" + entityTypeId + "/instances/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(authorisedUserToken)))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private Long createEntityType(String token, CreateEntityTypeRequest req) throws Exception {
        String body = objectMapper.writeValueAsString(req);
        String response = mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createRole(String token) throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateRoleRequest());
        String response = mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void addPermission(String token, Long roleId, String action, Long etId) throws Exception {
        String body = """
                {"action":"%s","entityTypeId":%d}
                """.formatted(action, etId);
        mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(token)))
                .andExpect(status().isCreated());
    }

    private Long createInstance(String token) throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        String response = mockMvc.perform(post("/api/v1/entity-types/" + entityTypeId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }
}
