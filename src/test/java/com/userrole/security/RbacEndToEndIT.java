package com.userrole.security;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import com.userrole.user.dto.CreateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RBAC End-to-End integration tests (E2E series from QE_TEST_PLAN.md §9.9).
 *
 * These tests span multiple transactions (login creates StoredTokens, assign-role
 * commits, etc.) and therefore CANNOT use @Transactional rollback.
 * Instead, each test method is preceded by reset_test_data.sql which truncates
 * all tables and re-seeds only the ADMIN role (Flyway V6 data).
 *
 * The @Sql annotation is applied at the METHOD level (before each test).
 *
 * Scenarios covered:
 *   E2E-001  Grant + exercise: user with CREATE on EntityTypeX can create instance
 *   E2E-002  Cross-type boundary: user with CREATE on X cannot CREATE on Y
 *   E2E-003  Permission boundary: user with CREATE on X cannot READ on X
 *   E2E-004  Permission addition: admin adds READ → user can now GET instance
 *   E2E-005  Role revocation: admin revokes role → user loses all access
 *   E2E-006  Role deletion: blocked while assigned; succeeds after revoke
 *   E2E-007  CREATE+UPDATE without DELETE: update succeeds, delete fails
 *   E2E-008  Role with all four permissions allows all CRUD operations
 *   E2E-009  Two users with different roles — each only accesses what they have
 *   E2E-010  Assign role → audit entry created; revoke role → second audit entry
 *   E2E-011  Rename role: audit history roleName snapshot is point-in-time
 *   E2E-012  passwordHash absent from every response in a full user lifecycle
 */
@SuppressWarnings("null")
@Sql(
    scripts  = "/sql/reset_test_data.sql",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class RbacEndToEndIT extends BaseApiTest {

    // =====================================================================
    // E2E-001: Grant and exercise — user with CREATE can create instance
    // =====================================================================
    @Test
    void e2e001_grantCreatePermission_userCanCreateInstance() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etId = createEntityType(admin);
        Long roleId = createRoleWithPermission(admin, "CREATE", etId);

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);
        assignRole(admin, userId, roleId);
        String userToken = loginAndGetToken(userReq.username(), "Password1!");

        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        mockMvc.perform(post("/api/v1/entity-types/" + etId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.entityTypeId").value(etId))
                .andExpect(jsonPath("$.data.createdBy").value(userId))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // =====================================================================
    // E2E-002: Cross-type boundary — CREATE on X ≠ CREATE on Y
    // =====================================================================
    @Test
    void e2e002_crossTypeBoundary_userCannotCreateOnUnpermittedEntityType() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etX = createEntityType(admin);
        Long etY = createEntityType(admin);
        Long roleId = createRoleWithPermission(admin, "CREATE", etX); // only on X

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);
        assignRole(admin, userId, roleId);
        String userToken = loginAndGetToken(userReq.username(), "Password1!");

        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        // Attempt CREATE on Y — must be 403
        mockMvc.perform(post("/api/v1/entity-types/" + etY + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // E2E-003: Permission boundary — CREATE on X ≠ READ on X
    // =====================================================================
    @Test
    void e2e003_permissionBoundary_createPermissionDoesNotGrantRead() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etId = createEntityType(admin);
        Long roleId = createRoleWithPermission(admin, "CREATE", etId);

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);
        assignRole(admin, userId, roleId);
        String userToken = loginAndGetToken(userReq.username(), "Password1!");

        // Create instance first (succeeds because user has CREATE)
        String createBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        String createResp = mockMvc.perform(post("/api/v1/entity-types/" + etId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long instanceId = objectMapper.readTree(createResp).path("data").path("id").asLong();

        // Attempt READ (GET) — must be 403 (READ not granted)
        mockMvc.perform(get("/api/v1/entity-types/" + etId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // E2E-004: Permission addition — admin adds READ → user can now GET
    // =====================================================================
    @Test
    void e2e004_addReadPermission_userGainsReadAccess() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etId = createEntityType(admin);
        Long roleId = createRoleWithPermission(admin, "CREATE", etId);

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);
        assignRole(admin, userId, roleId);
        String userToken = loginAndGetToken(userReq.username(), "Password1!");

        // Create an instance (admin has all permissions; use admin token)
        String createBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        String createResp = mockMvc.perform(post("/api/v1/entity-types/" + etId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long instanceId = objectMapper.readTree(createResp).path("data").path("id").asLong();

        // Confirm READ is denied before adding permission
        mockMvc.perform(get("/api/v1/entity-types/" + etId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());

        // Admin adds READ permission to the role
        addPermissionToRole(admin, roleId, "READ", etId);

        // User must re-login to get a JWT reflecting updated role permissions
        // (The JWT embeds roles by name; permissions are checked at request time
        //  from the DB — no re-login needed since PermissionChecker queries live DB)
        mockMvc.perform(get("/api/v1/entity-types/" + etId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(instanceId));
    }

    // =====================================================================
    // E2E-005: Role revocation — user loses all access after role revoked
    // =====================================================================
    @Test
    void e2e005_revokeRole_userLosesAllAccess() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etId = createEntityType(admin);
        Long roleId = createRoleWithPermission(admin, "CREATE", etId);

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);
        assignRole(admin, userId, roleId);
        String userToken = loginAndGetToken(userReq.username(), "Password1!");

        // Confirm access works before revoke
        String createBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        mockMvc.perform(post("/api/v1/entity-types/" + etId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isCreated());

        // Admin revokes the role
        mockMvc.perform(delete("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isNoContent());

        // User re-logs in to get a fresh JWT with no roles
        String freshToken = loginAndGetToken(userReq.username(), "Password1!");

        // Attempt CREATE — must now be 403
        mockMvc.perform(post("/api/v1/entity-types/" + etId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(freshToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // E2E-006: Role deletion — blocked while assigned; succeeds after revoke
    // =====================================================================
    @Test
    void e2e006_roleDeletion_blockedWhileAssigned_succeedsAfterRevoke() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etId = createEntityType(admin);
        Long roleId = createRoleWithPermission(admin, "CREATE", etId);

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);
        assignRole(admin, userId, roleId);
        String freshToken = loginAndGetToken(userReq.username(), "Password1!");

        // Attempt to delete role while user is assigned — must be 409
        mockMvc.perform(delete("/api/v1/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isConflict());

        // Revoke the role
        mockMvc.perform(delete("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isNoContent());

        // Delete role now — must succeed
        mockMvc.perform(delete("/api/v1/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isNoContent());

        // User (re-login, no role) attempts CREATE — must be 403
        String noRoleToken = loginAndGetToken(userReq.username(), "Password1!");
        String createBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        mockMvc.perform(post("/api/v1/entity-types/" + etId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(noRoleToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // E2E-007: CREATE+UPDATE without DELETE — update succeeds, delete fails
    // =====================================================================
    @Test
    void e2e007_createAndUpdatePermissions_deleteNotGranted() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etId = createEntityType(admin);
        Long roleId = createRoleWithPermissions(admin, new String[]{"CREATE", "UPDATE", "READ"}, etId);

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);
        assignRole(admin, userId, roleId);
        String userToken = loginAndGetToken(userReq.username(), "Password1!");

        // Create instance
        String createBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        String createResp = mockMvc.perform(post("/api/v1/entity-types/" + etId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long instanceId = objectMapper.readTree(createResp).path("data").path("id").asLong();

        // Update instance — must succeed (UPDATE granted)
        String updateBody = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateInstanceRequest());
        mockMvc.perform(put("/api/v1/entity-types/" + etId + "/instances/" + instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isOk());

        // Delete instance — must fail (DELETE not granted)
        mockMvc.perform(delete("/api/v1/entity-types/" + etId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // E2E-008: All four permissions — full CRUD allowed
    // =====================================================================
    @Test
    void e2e008_allFourPermissions_fullCrudAllowed() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etId = createEntityType(admin);
        Long roleId = createRoleWithPermissions(admin,
                new String[]{"CREATE", "READ", "UPDATE", "DELETE"}, etId);

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);
        assignRole(admin, userId, roleId);
        String userToken = loginAndGetToken(userReq.username(), "Password1!");

        // CREATE
        String createBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());
        String createResp = mockMvc.perform(post("/api/v1/entity-types/" + etId + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long instanceId = objectMapper.readTree(createResp).path("data").path("id").asLong();

        // READ
        mockMvc.perform(get("/api/v1/entity-types/" + etId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isOk());

        // UPDATE
        String updateBody = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateInstanceRequest());
        mockMvc.perform(put("/api/v1/entity-types/" + etId + "/instances/" + instanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isOk());

        // DELETE
        mockMvc.perform(delete("/api/v1/entity-types/" + etId + "/instances/" + instanceId)
                        .header("Authorization", jwtTestHelper.bearerHeader(userToken)))
                .andExpect(status().isNoContent());
    }

    // =====================================================================
    // E2E-009: Two users with different roles — each accesses only their scope
    // =====================================================================
    @Test
    void e2e009_twoUsersWithDifferentRoles_eachAccessesOnlyTheirScope() throws Exception {
        String admin = createAdminUserAndGetToken();

        Long etX = createEntityType(admin);
        Long etY = createEntityType(admin);

        // Role R1: CREATE on X; Role R2: CREATE on Y
        Long r1 = createRoleWithPermission(admin, "CREATE", etX);
        Long r2 = createRoleWithPermission(admin, "CREATE", etY);

        CreateUserRequest u1Req = TestDataFactory.createUserRequest("userX");
        Long u1Id = createUserWithToken(admin, u1Req);
        assignRole(admin, u1Id, r1);
        String u1Token = loginAndGetToken(u1Req.username(), "Password1!");

        CreateUserRequest u2Req = TestDataFactory.createUserRequest("userY");
        Long u2Id = createUserWithToken(admin, u2Req);
        assignRole(admin, u2Id, r2);
        String u2Token = loginAndGetToken(u2Req.username(), "Password1!");

        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateInstanceRequest());

        // U1 can CREATE on X
        mockMvc.perform(post("/api/v1/entity-types/" + etX + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(u1Token)))
                .andExpect(status().isCreated());

        // U1 cannot CREATE on Y
        mockMvc.perform(post("/api/v1/entity-types/" + etY + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(u1Token)))
                .andExpect(status().isForbidden());

        // U2 can CREATE on Y
        mockMvc.perform(post("/api/v1/entity-types/" + etY + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(u2Token)))
                .andExpect(status().isCreated());

        // U2 cannot CREATE on X
        mockMvc.perform(post("/api/v1/entity-types/" + etX + "/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(u2Token)))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // E2E-010: Assign → audit entry; Revoke → second audit entry
    // =====================================================================
    @Test
    void e2e010_assignAndRevoke_auditHistoryContainsBothEntries() throws Exception {
        String admin = createAdminUserAndGetToken();
        Long adminRoleId = findAdminRoleId(admin);

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);

        // Assign
        assignRole(admin, userId, adminRoleId);

        // Revoke
        mockMvc.perform(delete("/api/v1/users/" + userId + "/roles/" + adminRoleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isNoContent());

        // Check audit history — must have 2 entries: ASSIGNED then REVOKED
        mockMvc.perform(get("/api/v1/users/" + userId + "/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data.data[?(@.action=='ASSIGNED')]").exists())
                .andExpect(jsonPath("$.data.data[?(@.action=='REVOKED')]").exists())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // =====================================================================
    // E2E-011: Rename role — audit history roleName snapshot unchanged
    // =====================================================================
    @Test
    void e2e011_renameRole_auditHistorySnapshotPreserved() throws Exception {
        String admin = createAdminUserAndGetToken();

        String originalName = "SNAPSHOT_ROLE_" + java.util.UUID.randomUUID().toString().substring(0, 6);
        String renamedName  = "RENAMED_ROLE_"  + java.util.UUID.randomUUID().toString().substring(0, 6);

        // Create role with the original name
        String createRoleBody = """
                {"name":"%s","description":"test"}
                """.formatted(originalName);
        String roleResp = mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoleBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long roleId = objectMapper.readTree(roleResp).path("data").path("id").asLong();

        CreateUserRequest userReq = TestDataFactory.createUserRequest("worker");
        Long userId = createUserWithToken(admin, userReq);

        // Assign role — audit entry captures originalName as snapshot
        assignRole(admin, userId, roleId);

        // Admin renames the role
        String updateRoleBody = """
                {"name":"%s","description":"renamed"}
                """.formatted(renamedName);
        mockMvc.perform(put("/api/v1/roles/" + roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRoleBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isOk());

        // Audit history roleName must still reflect the ORIGINAL name (point-in-time snapshot)
        mockMvc.perform(get("/api/v1/users/" + userId + "/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].roleName").value(originalName))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // =====================================================================
    // E2E-012: passwordHash absent across full user lifecycle
    // =====================================================================
    @Test
    void e2e012_passwordHashAbsentAcrossFullUserLifecycle() throws Exception {
        String admin = createAdminUserAndGetToken();

        CreateUserRequest userReq = TestDataFactory.createUserRequest("lifecycle");
        String createBody = objectMapper.writeValueAsString(userReq);

        // POST /api/v1/users — create
        String createResp = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long userId = objectMapper.readTree(createResp).path("data").path("id").asLong();
        assertNoPasswordHash(createResp);

        // GET /api/v1/users/{id}
        String getResp = mockMvc.perform(get("/api/v1/users/" + userId)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoPasswordHash(getResp);

        // GET /api/v1/users (list)
        String listResp = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoPasswordHash(listResp);

        // PUT /api/v1/users/{id} (update)
        String updateBody = objectMapper.writeValueAsString(TestDataFactory.uniqueUpdateUserRequest());
        String updateResp = mockMvc.perform(put("/api/v1/users/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoPasswordHash(updateResp);

        // GET /api/v1/users/{id}/roles
        String rolesResp = mockMvc.perform(get("/api/v1/users/" + userId + "/roles")
                        .header("Authorization", jwtTestHelper.bearerHeader(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoPasswordHash(rolesResp);
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private Long createEntityType(String adminToken) throws Exception {
        String body = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateEntityTypeRequest());
        String resp = mockMvc.perform(post("/api/v1/entity-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private Long createRoleWithPermission(String adminToken, String action, Long etId) throws Exception {
        return createRoleWithPermissions(adminToken, new String[]{action}, etId);
    }

    private Long createRoleWithPermissions(String adminToken, String[] actions, Long etId) throws Exception {
        String roleBody = objectMapper.writeValueAsString(TestDataFactory.uniqueCreateRoleRequest());
        String roleResp = mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long roleId = objectMapper.readTree(roleResp).path("data").path("id").asLong();
        for (String action : actions) {
            addPermissionToRole(adminToken, roleId, action, etId);
        }
        return roleId;
    }

    private void addPermissionToRole(String adminToken, Long roleId, String action, Long etId)
            throws Exception {
        String permBody = """
                {"action":"%s","entityTypeId":%d}
                """.formatted(action, etId);
        mockMvc.perform(post("/api/v1/roles/" + roleId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(permBody)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isCreated());
    }

    private void assignRole(String adminToken, Long userId, Long roleId) throws Exception {
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    private void assertNoPasswordHash(String responseBody) {
        org.junit.jupiter.api.Assertions.assertFalse(
                responseBody.contains("passwordHash"),
                "Response must not contain 'passwordHash': " + responseBody);
        org.junit.jupiter.api.Assertions.assertFalse(
                responseBody.contains("password_hash"),
                "Response must not contain 'password_hash': " + responseBody);
    }
}
