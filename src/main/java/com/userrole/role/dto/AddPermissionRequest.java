package com.userrole.role.dto;

import com.userrole.role.Action;
import jakarta.validation.constraints.NotNull;

public record AddPermissionRequest(
        @NotNull(message = "Action must not be null")
        Action action,

        @NotNull(message = "EntityType ID must not be null")
        Long entityTypeId
) {
}
