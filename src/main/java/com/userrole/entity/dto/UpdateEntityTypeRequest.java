package com.userrole.entity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateEntityTypeRequest(
        @Size(min = 1, max = 255, message = "Entity type name must be between 1 and 255 characters if provided")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @NotNull(message = "version is required")
        Long version
) {
}
