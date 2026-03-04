package com.userrole.common;

/**
 * Thrown when a requested resource does not exist.
 * Maps to HTTP 404.
 */
public class ResourceNotFoundException extends UserRoleException {

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
}
