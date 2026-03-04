package com.userrole.common;

/**
 * Port interface used by the role module to query user assignment counts without
 * directly coupling to the user module's implementation classes.
 *
 * Implemented by: com.userrole.user.UserServiceImpl (adapter in user module)
 * Consumed by: com.userrole.role.RoleServiceImpl (in deleteRole conflict check)
 */
public interface RoleAssignmentQueryPort {

    /**
     * Returns the number of users currently assigned to the given role.
     * A non-zero count blocks role deletion.
     *
     * @param roleId the role to check
     * @return count of active user-role assignments
     */
    long countUsersAssignedToRole(Long roleId);
}
