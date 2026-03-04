package com.userrole.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    @Query("SELECT p FROM Permission p WHERE p.role.id = :roleId")
    List<Permission> findAllByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT COUNT(p) > 0 FROM Permission p WHERE p.role.id = :roleId AND p.action = :action AND p.entityTypeId = :entityTypeId")
    boolean existsByRoleIdAndActionAndEntityTypeId(
            @Param("roleId") Long roleId,
            @Param("action") Action action,
            @Param("entityTypeId") Long entityTypeId
    );

    @Query("SELECT p FROM Permission p WHERE p.role.name IN :roleNames AND p.action = :action AND p.entityTypeId = :entityTypeId")
    List<Permission> findByRoleNamesAndActionAndEntityTypeId(
            @Param("roleNames") List<String> roleNames,
            @Param("action") Action action,
            @Param("entityTypeId") Long entityTypeId
    );
}
