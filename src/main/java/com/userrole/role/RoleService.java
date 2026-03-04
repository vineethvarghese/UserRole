package com.userrole.role;

import com.userrole.role.dto.*;

import java.util.List;

public interface RoleService {

    RoleResponse createRole(CreateRoleRequest request);

    RoleResponse getRoleById(Long id);

    List<RoleResponse> listRoles();

    RoleResponse updateRole(Long id, UpdateRoleRequest request);

    void deleteRole(Long id);

    PermissionResponse addPermission(Long roleId, Action action, Long entityTypeId);

    void removePermission(Long roleId, Long permissionId);

    List<PermissionResponse> getPermissionsForRole(Long roleId);
}
