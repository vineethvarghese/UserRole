package com.userrole.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 1, max = 255, message = "Full name must be between 1 and 255 characters if provided")
        String fullName,

        @Size(max = 255, message = "Department must not exceed 255 characters")
        String department,

        @Email(message = "Email must be a valid email address if provided")
        String email,

        @Size(max = 50, message = "Phone must not exceed 50 characters")
        String phone,

        @NotNull(message = "version is required")
        Long version
) {
}
