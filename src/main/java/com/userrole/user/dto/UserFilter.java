package com.userrole.user.dto;

/**
 * Filter parameters for the list-users endpoint.
 * Both fields are optional; null means "no filter applied".
 */
public record UserFilter(
        String department,
        String name
) {
}
