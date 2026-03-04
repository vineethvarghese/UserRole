package com.userrole.entity.dto;

import com.userrole.entity.EntityInstance;

import java.time.Instant;

public record EntityInstanceResponse(
        Long id,
        Long entityTypeId,
        String name,
        String description,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static EntityInstanceResponse from(EntityInstance instance) {
        return new EntityInstanceResponse(
                instance.getId(),
                instance.getEntityType().getId(),
                instance.getName(),
                instance.getDescription(),
                instance.getCreatedBy(),
                instance.getCreatedAt(),
                instance.getUpdatedAt(),
                instance.getVersion()
        );
    }
}
