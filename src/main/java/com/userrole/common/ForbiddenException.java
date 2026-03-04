package com.userrole.common;

/**
 * Thrown when the caller is authenticated but does not have permission to perform the requested action.
 * Maps to HTTP 403.
 */
public class ForbiddenException extends UserRoleException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
