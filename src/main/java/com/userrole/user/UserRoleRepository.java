package com.userrole.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("SELECT ur FROM UserRole ur WHERE ur.id.userId = :userId")
    List<UserRole> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(ur) FROM UserRole ur WHERE ur.id.roleId = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRole ur WHERE ur.id.userId = :userId AND ur.id.roleId = :roleId")
    boolean existsByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
