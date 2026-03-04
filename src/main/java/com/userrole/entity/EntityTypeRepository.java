package com.userrole.entity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityTypeRepository extends JpaRepository<EntityType, Long> {

    boolean existsByName(String name);
}
