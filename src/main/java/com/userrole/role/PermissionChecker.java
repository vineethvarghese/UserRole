package com.userrole.role;

import java.util.List;

/**
 * Port interface consumed by the entity module to check whether a user's roles
 * grant a specific action on a specific entity type.
 *
 * Implemented by: PermissionCheckerImpl in the role module.
 * Consumed by: EntityInstanceServiceImpl in the entity module.
 */
public interface PermissionChecker {

    /**
     * Returns true if at least one of the supplied role names grants the given action
     * on the given entity type.
     *
     * @param roleNames    the names of the caller's current roles
     * @param action       the action being attempted
     * @param entityTypeId the entity type being acted upon
     * @return true if access is granted
     */
    boolean hasPermission(List<String> roleNames, Action action, Long entityTypeId);
}
