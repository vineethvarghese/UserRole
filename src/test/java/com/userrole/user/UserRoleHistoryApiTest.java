package com.userrole.user;

import com.userrole.support.BaseApiTest;
import com.userrole.support.TestDataFactory;
import com.userrole.user.dto.CreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration / API tests for the role change audit history endpoint.
 *
 * Covers UC-USER-009: Get role change history for user.
 * - Admin can view history of any user
 * - Self can view own history
 * - Other regular user is denied (403)
 * - History contains ASSIGNED entry after role assignment
 * - History contains REVOKED entry after role revocation
 * - Pagination params are reflected in meta
 * - passwordHash never appears in any response
 */
@SuppressWarnings("null")
@Transactional
class UserRoleHistoryApiTest extends BaseApiTest {

    private String adminToken;
    private Long adminRoleId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminUserAndGetToken();
        adminRoleId = findAdminRoleId(adminToken);
    }

    @Test
    void getRoleChangeHistory_asAdmin_returns200WithPaginatedEntries() throws Exception {
        // Create a user and assign a role to generate an audit entry
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        assignRole(userId, adminRoleId);

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data[0].action").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.data[0].roleName").value(com.userrole.common.RoleConstants.ADMIN))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRoleChangeHistory_afterAssignAndRevoke_containsBothEntries() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        // Assign then revoke — generates two audit entries
        assignRole(userId, adminRoleId);
        revokeRole(userId, adminRoleId);

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data.data[?(@.action=='ASSIGNED')]").exists())
                .andExpect(jsonPath("$.data.data[?(@.action=='REVOKED')]").exists())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRoleChangeHistory_withNoRoleChanges_returnsEmptyList() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data").isEmpty())
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRoleChangeHistory_asSelf_returns200() throws Exception {
        CreateUserRequest req = TestDataFactory.uniqueCreateUserRequest();
        Long userId = createUserWithToken(adminToken, req);
        String selfToken = loginAndGetToken(req.username(), "Password1!");

        assignRole(userId, adminRoleId);

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(selfToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRoleChangeHistory_asOtherRegularUser_returns403() throws Exception {
        Long targetUserId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        CreateUserRequest otherReq = TestDataFactory.uniqueCreateUserRequest();
        createUserWithToken(adminToken, otherReq);
        String otherToken = loginAndGetToken(otherReq.username(), "Password1!");

        mockMvc.perform(get("/api/v1/users/" + targetUserId + "/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRoleChangeHistory_withNonExistentUser_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRoleChangeHistory_paginationMetaIsPresent() throws Exception {
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles/history")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.total").isNumber())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getRoleChangeHistory_auditEntryContainsSnapshotOfRoleName() throws Exception {
        // The roleName in the audit entry is a snapshot — it is stored at assignment time
        Long userId = createUserWithToken(adminToken, TestDataFactory.uniqueCreateUserRequest());
        assignRole(userId, adminRoleId);

        mockMvc.perform(get("/api/v1/users/" + userId + "/roles/history")
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].roleName").value(com.userrole.common.RoleConstants.ADMIN))
                .andExpect(jsonPath("$.data.data[0].roleId").value(adminRoleId))
                .andExpect(jsonPath("$.data.data[0].performedBy").isNumber())
                .andExpect(jsonPath("$.data.data[0].performedAt").isNotEmpty())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // ---- helpers ----

    private void assignRole(Long userId, Long roleId) throws Exception {
        mockMvc.perform(post("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }

    private void revokeRole(Long userId, Long roleId) throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + userId + "/roles/" + roleId)
                        .header("Authorization", jwtTestHelper.bearerHeader(adminToken)))
                .andExpect(status().isNoContent());
    }
}
