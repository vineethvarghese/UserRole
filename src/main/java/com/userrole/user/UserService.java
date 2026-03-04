package com.userrole.user;

import com.userrole.common.PageRequest;
import com.userrole.common.PageResponse;
import com.userrole.role.dto.RoleResponse;
import com.userrole.user.dto.*;

import java.util.List;
import java.util.Set;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUserById(Long id, Long requestingUserId, List<String> requestingUserRoles);

    PageResponse<UserResponse> listUsers(UserFilter filter, PageRequest page);

    UserResponse updateUser(Long id, UpdateUserRequest request, Long requestingUserId, List<String> requestingUserRoles);

    void deleteUser(Long id);

    void assignRole(Long userId, Long roleId, Long performedBy);

    void revokeRole(Long userId, Long roleId, Long performedBy);

    List<RoleResponse> getRolesForUser(Long userId);

    Set<Long> getRoleIdsForUser(Long userId);

    PageResponse<UserRoleAuditEntry> getRoleChangeHistory(Long userId, PageRequest page);

    /**
     * Looks up an active user by username. Used by auth.local at login time.
     * Throws ResourceNotFoundException if the user does not exist or is inactive.
     */
    User findByUsername(String username);

    /**
     * Looks up an active user by id. Used by auth.local at token refresh time.
     * Throws ResourceNotFoundException if the user does not exist or is inactive.
     */
    User findById(Long id);
}
