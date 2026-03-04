package com.userrole.entity.dto;

import com.userrole.entity.EntityType;

import java.time.Instant;

public record EntityTypeResponse(
        Long id,
        String name,
        String description,
        Instant createdAt,
        Long version
) {
    public static EntityTypeResponse from(EntityType entityType) {
        return new EntityTypeResponse(
                entityType.getId(),
                entityType.getName(),
                entityType.getDescription(),
                entityType.getCreatedAt(),
                entityType.getVersion()
        );
    }
}
