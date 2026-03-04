package com.userrole.support;

import com.userrole.user.dto.CreateUserRequest;
import com.userrole.role.dto.CreateRoleRequest;
import com.userrole.role.dto.UpdateRoleRequest;
import com.userrole.entity.dto.CreateEntityTypeRequest;
import com.userrole.entity.dto.CreateInstanceRequest;
import com.userrole.entity.dto.UpdateEntityTypeRequest;
import com.userrole.entity.dto.UpdateInstanceRequest;
import com.userrole.user.dto.UpdateUserRequest;

import java.util.UUID;

/**
 * Factory for unique test request DTOs.
 *
 * Every factory method uses UUID.randomUUID() to generate unique field values,
 * preventing inter-test contamination even when tests run in the same DB transaction.
 *
 * All methods are static — no instantiation needed.
 */
public final class TestDataFactory {

    private TestDataFactory() {
        // Utility class
    }

    // ---- User DTOs ----

    /**
     * Returns a valid CreateUserRequest with unique username and email.
     * The plain-text password is "Password1!" — simple enough to remember in tests.
     */
    public static CreateUserRequest uniqueCreateUserRequest() {
        String uid = shortUuid();
        return new CreateUserRequest(
                "user_" + uid,
                "Password1!",
                "Test User " + uid,
                "Engineering",
                "user_" + uid + "@example.com",
                "+1-555-0100"
        );
    }

    /**
     * Returns a CreateUserRequest with a custom username prefix for readability in tests.
     */
    public static CreateUserRequest createUserRequest(String usernamePrefix) {
        String uid = shortUuid();
        return new CreateUserRequest(
                usernamePrefix + "_" + uid,
                "Password1!",
                "Test User " + uid,
                "Engineering",
                usernamePrefix + "_" + uid + "@example.com",
                null
        );
    }

    /**
     * Returns an UpdateUserRequest with new unique values.
     */
    public static UpdateUserRequest uniqueUpdateUserRequest() {
        String uid = shortUuid();
        return new UpdateUserRequest(
                "Updated Name " + uid,
                "Finance",
                "updated_" + uid + "@example.com",
                null,
                0L
        );
    }

    // ---- Role DTOs ----

    public static CreateRoleRequest uniqueCreateRoleRequest() {
        return new CreateRoleRequest("ROLE_" + shortUuid(), "Test role description");
    }

    public static CreateRoleRequest createRoleRequest(String name) {
        return new CreateRoleRequest(name, "Description for " + name);
    }

    public static UpdateRoleRequest uniqueUpdateRoleRequest() {
        return new UpdateRoleRequest("UPDATED_ROLE_" + shortUuid(), "Updated description", 0L);
    }

    // ---- Entity Type DTOs ----

    public static CreateEntityTypeRequest uniqueCreateEntityTypeRequest() {
        return new CreateEntityTypeRequest("EntityType_" + shortUuid(), "A test entity type");
    }

    public static CreateEntityTypeRequest createEntityTypeRequest(String name) {
        return new CreateEntityTypeRequest(name, "Description for " + name);
    }

    public static UpdateEntityTypeRequest uniqueUpdateEntityTypeRequest() {
        return new UpdateEntityTypeRequest("UpdatedType_" + shortUuid(), "Updated description", 0L);
    }

    // ---- Entity Instance DTOs ----

    public static CreateInstanceRequest uniqueCreateInstanceRequest() {
        return new CreateInstanceRequest("Instance_" + shortUuid(), "A test instance");
    }

    public static UpdateInstanceRequest uniqueUpdateInstanceRequest() {
        return new UpdateInstanceRequest("UpdatedInstance_" + shortUuid(), "Updated instance description", 0L);
    }

    // ---- Internal helpers ----

    /**
     * Returns the first 8 characters of a random UUID — short enough for readable test data,
     * unique enough for test isolation.
     */
    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
