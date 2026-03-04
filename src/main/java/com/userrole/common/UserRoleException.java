package com.userrole.common;

/**
 * Base unchecked exception for all domain exceptions in the UserRole service.
 * All subclasses are unchecked — callers are not forced to declare or catch them,
 * but GlobalExceptionHandler maps them to the appropriate HTTP response.
 */
public class UserRoleException extends RuntimeException {

    private final String errorCode;

    public UserRoleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
