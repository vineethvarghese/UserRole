package com.userrole.role.dto;

import com.userrole.role.Action;
import com.userrole.role.Permission;

public record PermissionResponse(
        Long id,
        Long roleId,
        Action action,
        Long entityTypeId
) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getRole().getId(),
                permission.getAction(),
                permission.getEntityTypeId()
        );
    }
}
