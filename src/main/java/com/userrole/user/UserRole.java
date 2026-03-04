package com.userrole.user;

import jakarta.persistence.*;

/**
 * Join entity representing the many-to-many relationship between User and Role.
 * Uses @EmbeddedId with a composite key as specified in the implementation plan.
 */
@Entity
@Table(name = "user_roles")
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    protected UserRole() {
    }

    public UserRole(Long userId, Long roleId) {
        this.id = new UserRoleId(userId, roleId);
    }

    public UserRoleId getId() {
        return id;
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public Long getRoleId() {
        return id.getRoleId();
    }
}
