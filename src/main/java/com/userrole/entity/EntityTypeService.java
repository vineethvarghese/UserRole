package com.userrole.entity;

import com.userrole.entity.dto.CreateEntityTypeRequest;
import com.userrole.entity.dto.EntityTypeResponse;
import com.userrole.entity.dto.UpdateEntityTypeRequest;

import java.util.List;

public interface EntityTypeService {

    EntityTypeResponse createEntityType(CreateEntityTypeRequest request);

    EntityTypeResponse getEntityTypeById(Long id);

    List<EntityTypeResponse> listEntityTypes();

    EntityTypeResponse updateEntityType(Long id, UpdateEntityTypeRequest request);

    void deleteEntityType(Long id);
}
