package com.userrole.role.dto;

import com.userrole.role.Role;

import java.time.Instant;
import java.util.List;

public record RoleResponse(
        Long id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<PermissionResponse> permissions,
        Long version
) {
    /** Maps from entity, including full permission list. */
    public static RoleResponse from(Role role) {
        List<PermissionResponse> perms = role.getPermissions().stream()
                .map(PermissionResponse::from)
                .toList();
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                perms,
                role.getVersion()
        );
    }

    /** Maps from entity without loading the permissions collection (used in list contexts). */
    public static RoleResponse fromWithoutPermissions(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                List.of(),
                role.getVersion()
        );
    }
}
