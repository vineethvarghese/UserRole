package com.userrole.user;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_role_audit_log")
public class UserRoleAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "performed_by", nullable = false)
    private Long performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    protected UserRoleAuditLog() {
    }

    public UserRoleAuditLog(Long userId, Long roleId, String roleName,
                            AuditAction action, Long performedBy) {
        this.userId = userId;
        this.roleId = roleId;
        this.roleName = roleName;
        this.action = action;
        this.performedBy = performedBy;
        this.performedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public AuditAction getAction() {
        return action;
    }

    public Long getPerformedBy() {
        return performedBy;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }
}
