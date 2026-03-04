package com.userrole.user.dto;

import com.userrole.user.AuditAction;
import com.userrole.user.UserRoleAuditLog;

import java.time.Instant;

public record UserRoleAuditEntry(
        Long id,
        Long userId,
        Long roleId,
        String roleName,
        AuditAction action,
        Long performedBy,
        Instant performedAt
) {
    public static UserRoleAuditEntry from(UserRoleAuditLog log) {
        return new UserRoleAuditEntry(
                log.getId(),
                log.getUserId(),
                log.getRoleId(),
                log.getRoleName(),
                log.getAction(),
                log.getPerformedBy(),
                log.getPerformedAt()
        );
    }
}
