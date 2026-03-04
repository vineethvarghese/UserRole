package com.userrole.common;

/**
 * Thrown when request data fails business-rule validation beyond bean-validation constraints.
 * Maps to HTTP 400.
 */
public class ValidationException extends UserRoleException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
