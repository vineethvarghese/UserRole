-- V6: Seed the ADMIN role
-- Uses a conditional insert so this is idempotent if re-run in a new environment
-- No default admin user is seeded in v1 — first admin user must be created externally

INSERT INTO roles (name, description, created_at, updated_at)
SELECT 'ADMIN', 'System administrator role with full access', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ADMIN'
);
