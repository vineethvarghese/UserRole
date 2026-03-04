package com.userrole.entity;

import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.PageRequest;
import com.userrole.common.PageResponse;
import com.userrole.entity.dto.CreateInstanceRequest;
import com.userrole.entity.dto.EntityInstanceResponse;
import com.userrole.entity.dto.UpdateInstanceRequest;

public interface EntityInstanceService {

    EntityInstanceResponse createInstance(Long typeId, CreateInstanceRequest request, TokenClaims claims);

    EntityInstanceResponse getInstanceById(Long typeId, Long id, TokenClaims claims);

    PageResponse<EntityInstanceResponse> listInstances(Long typeId, PageRequest page, TokenClaims claims);

    EntityInstanceResponse updateInstance(Long typeId, Long id, UpdateInstanceRequest request, TokenClaims claims);

    void deleteInstance(Long typeId, Long id, TokenClaims claims);
}
