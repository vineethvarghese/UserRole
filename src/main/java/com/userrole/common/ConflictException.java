package com.userrole.common;

/**
 * Thrown when an operation conflicts with existing state (e.g. duplicate resource, in-use constraint).
 * Maps to HTTP 409.
 */
public class ConflictException extends UserRoleException {

    public ConflictException(String message) {
        super("CONFLICT", message);
    }
}
