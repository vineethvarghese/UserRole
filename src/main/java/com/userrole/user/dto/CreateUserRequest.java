package com.userrole.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Username must not be blank")
        @Size(max = 255, message = "Username must not exceed 255 characters")
        String username,

        @NotBlank(message = "Password must not be blank")
        String password,

        @NotBlank(message = "Full name must not be blank")
        @Size(max = 255, message = "Full name must not exceed 255 characters")
        String fullName,

        @Size(max = 255, message = "Department must not exceed 255 characters")
        String department,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email,

        @Size(max = 50, message = "Phone must not exceed 50 characters")
        String phone
) {
}
