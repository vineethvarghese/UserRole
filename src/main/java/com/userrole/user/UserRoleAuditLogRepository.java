package com.userrole.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleAuditLogRepository extends JpaRepository<UserRoleAuditLog, Long> {

    @Query("SELECT a FROM UserRoleAuditLog a WHERE a.userId = :userId ORDER BY a.performedAt DESC")
    Page<UserRoleAuditLog> findAllByUserId(@Param("userId") Long userId, Pageable pageable);
}
