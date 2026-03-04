package com.userrole.common;

/**
 * Well-known role name constants shared across modules.
 * Using a constant prevents magic string duplication and makes refactoring safe.
 */
public final class RoleConstants {

    /** The system administrator role name. Seeded via Flyway V6. */
    public static final String ADMIN = "ADMIN";

    private RoleConstants() {
        // Utility class — not instantiable
    }
}
