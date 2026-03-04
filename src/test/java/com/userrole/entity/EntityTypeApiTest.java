package com.userrole.entity;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import com.userrole.entity.dto.CreateEntityTypeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration / API tests for the Entity Type endpoints.
 *
 * Covers UC-ENT-001 through UC-ENT-005:
 * - UC-ENT-001: Create entity type (admin, validation, duplicate name, non-admin denied)
 * - UC-ENT-002: List entity types (authenticated access — any authenticated user)
 * - UC-ENT-003: Get entity type by ID (authenticated access, not found)
 * - UC-ENT-004: Update entity type (admin, not found, duplicate name)
 * - UC-ENT-005: Delete entity type (no instances, blocked if instances exist, not found)
 *
 * All tests are @Transactional — rolled back after each test.
 */
@SuppressWarnings("null")
@Transactional
class EntityTypeApiTest extends BaseApiTest {

    private String adminToken;
    private String regularUserToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminUserAndGetToken();
        Object[] result = createRegularUserAndGetToken(adminToken);
        regularUserToken = (String) result[1];
    }

    // =====================================================================
    // UC-ENT-001: Create entity type
    // =====================================================================

    @Test
    void createEntityType_withValidRequest_returns201AndEntityTypeResponse() throws Exception {
        CreateEntityTypeRequest req = TestDataFactory.uniqueCreateEntityTypeRequest();
        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value(req.name()))
                .andExpect(jsonPath("$.data.description").value(req.description()))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void createEntityType_withDuplicateName_returns409() throws Exception {
        CreateEntityTypeRequest req = TestDataFactory.uniqueCreateEntityTypeRequest();
        createEntityType(adminToken, req);

        String body = objectMapper.writeValueAsString(req);
        mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void createEntityType_withBlankName_returns400ValidationError() throws Exception {
        String body = """
                {"name":"","description":"Some description"}
                """;

        mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createEntityType_asRegularUser_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateEntityTypeRequest());

        mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEntityType_withoutToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateEntityTypeRequest());

        mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // UC-ENT-002: List entity types (any authenticated user)
    // =====================================================================

    @Test
    void listEntityTypes_asAdmin_returns200WithList() throws Exception {
        createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());

        mockMvc.perform(get("/api/v1/entity-types")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").exists());
    }

    @Test
    void listEntityTypes_asRegularUser_returns200() throws Exception {
        // Any authenticated user can list entity types
        createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());

        mockMvc.perform(get("/api/v1/entity-types")
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listEntityTypes_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/entity-types"))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // UC-ENT-003: Get entity type by ID
    // =====================================================================

    @Test
    void getEntityTypeById_withValidId_returns200() throws Exception {
        Long typeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());

        mockMvc.perform(get("/api/v1/entity-types/" + typeId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(typeId));
    }

    @Test
    void getEntityTypeById_asRegularUser_returns200() throws Exception {
        Long typeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());

        mockMvc.perform(get("/api/v1/entity-types/" + typeId)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(typeId));
    }

    @Test
    void getEntityTypeById_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/entity-types/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // =====================================================================
    // UC-ENT-004: Update entity type
    // =====================================================================

    @Test
    void updateEntityType_asAdmin_returns200WithUpdatedResponse() throws Exception {
        Long typeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());
        var update = TestDataFactory.uniqueUpdateEntityTypeRequest();
        String body = objectMapper.writeValueAsString(update);

        mockMvc.perform(put("/api/v1/entity-types/" + typeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(update.name()))
                .andExpect(jsonPath("$.data.description").value(update.description()));
    }

    @Test
    void updateEntityType_withNonExistentId_returns404() throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateEntityTypeRequest());

        mockMvc.perform(put("/api/v1/entity-types/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateEntityType_asRegularUser_returns403() throws Exception {
        Long typeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateEntityTypeRequest());

        mockMvc.perform(put("/api/v1/entity-types/" + typeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // UC-ENT-005: Delete entity type
    // =====================================================================

    @Test
    void deleteEntityType_withNoInstances_returns204() throws Exception {
        Long typeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());

        mockMvc.perform(delete("/api/v1/entity-types/" + typeId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteEntityType_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/entity-types/999999")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteEntityType_asRegularUser_returns403() throws Exception {
        Long typeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());

        mockMvc.perform(delete("/api/v1/entity-types/" + typeId)
                        .header("Authorization", jwtTestHelper.bearerHeader(regularUserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteEntityType_withExistingInstances_returns409() throws Exception {
        // Set up: role with CREATE permission on entity type
        Long typeId = createEntityType(adminToken, TestDataFactory.uniqueCreateEntityTypeRequest());
        Long roleId = createRole(adminToken);
        addPermission(adminToken, roleId, "CREATE", typeId);

        // Create a user, assign the role, create an instance
        com.userrole.user.dto.CreateUserRequest userReq = TestDataFactory.uniqueCreateUserRequest();
        Long userId = createUserWithToken(adminToken, userReq);
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
        String userToken = loginAndGetToken(userReq.username(), "Password1!");

        String instanceBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        mockMvc.perform(post("/api/v1/entity-types/" + typeId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instanceBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isCreated());

        // Now try to delete the entity type — must fail
        mockMvc.perform(delete("/api/v1/entity-types/" + typeId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
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
}
