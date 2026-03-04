package com.userrole.user;

/**
 * The action recorded in the user-role audit log.
 * Stored as VARCHAR in the database via @Enumerated(EnumType.STRING).
 */
public enum AuditAction {
    ASSIGNED,
    REVOKED
}
