package com.userrole.entity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntityInstanceRepository extends JpaRepository<EntityInstance, Long> {

    @Query("SELECT i FROM EntityInstance i WHERE i.entityType.id = :typeId")
    Page<EntityInstance> findAllByEntityTypeId(@Param("typeId") Long typeId, Pageable pageable);

    @Query("SELECT COUNT(i) FROM EntityInstance i WHERE i.entityType.id = :typeId")
    long countByEntityTypeId(@Param("typeId") Long typeId);
}
