package com.userrole.user.dto;

import com.userrole.user.User;

import java.time.Instant;

/**
 * Safe user representation returned by the API.
 * passwordHash is intentionally excluded — it must never appear in any response.
 */
public record UserResponse(
        Long id,
        String username,
        String fullName,
        String department,
        String email,
        String phone,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getDepartment(),
                user.getEmail(),
                user.getPhone(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion()
        );
    }
}
