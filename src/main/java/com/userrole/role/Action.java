package com.userrole.role;

/**
 * The set of actions a Role can grant on an EntityType.
 * Stored as VARCHAR in the database via @Enumerated(EnumType.STRING).
 */
public enum Action {
    CREATE,
    READ,
    UPDATE,
    DELETE
}
