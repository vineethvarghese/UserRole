-- reset_test_data.sql
-- Truncates all tables in reverse FK order and re-seeds the ADMIN role (from V6).
-- Used by RbacEndToEndIT before each test method.
-- H2-specific: SET REFERENTIAL_INTEGRITY is used to bypass FK checks during truncation.

SET REFERENTIAL_INTEGRITY FALSE;

TRUNCATE TABLE user_role_audit_log;
TRUNCATE TABLE user_roles;
TRUNCATE TABLE stored_tokens;
TRUNCATE TABLE entity_instances;
TRUNCATE TABLE permissions;
TRUNCATE TABLE entity_types;
TRUNCATE TABLE roles;
TRUNCATE TABLE users;

SET REFERENTIAL_INTEGRITY TRUE;

-- Re-seed the ADMIN role (mirrors V6__seed_admin_role.sql)
INSERT INTO roles (name, description, created_at, updated_at)
SELECT 'ADMIN', 'System administrator role with full access', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ADMIN'
);
