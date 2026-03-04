package com.userrole.entity;

import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.ConflictException;
import com.userrole.common.ForbiddenException;
import com.userrole.common.PageRequest;
import com.userrole.common.PageResponse;
import com.userrole.common.ResourceNotFoundException;
import com.userrole.entity.dto.CreateInstanceRequest;
import com.userrole.entity.dto.EntityInstanceResponse;
import com.userrole.entity.dto.UpdateInstanceRequest;
import com.userrole.role.Action;
import com.userrole.role.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class EntityInstanceServiceImpl implements EntityInstanceService {

    private final EntityInstanceRepository entityInstanceRepository;
    private final EntityTypeRepository entityTypeRepository;
    private final PermissionChecker permissionChecker;

    public EntityInstanceServiceImpl(
            EntityInstanceRepository entityInstanceRepository,
            EntityTypeRepository entityTypeRepository,
            PermissionChecker permissionChecker
    ) {
        this.entityInstanceRepository = entityInstanceRepository;
        this.entityTypeRepository = entityTypeRepository;
        this.permissionChecker = permissionChecker;
    }

    @Override
    @Transactional
    public EntityInstanceResponse createInstance(Long typeId, CreateInstanceRequest request, TokenClaims claims) {
        EntityType entityType = findEntityTypeOrThrow(typeId);
        checkPermission(claims, Action.CREATE, typeId);

        EntityInstance instance = new EntityInstance(
                entityType,
                request.name(),
                request.description(),
                claims.userId()
        );
        EntityInstance saved = entityInstanceRepository.save(instance);
        return EntityInstanceResponse.from(saved);
    }

    @Override
    public EntityInstanceResponse getInstanceById(Long typeId, Long id, TokenClaims claims) {
        findEntityTypeOrThrow(typeId);
        checkPermission(claims, Action.READ, typeId);

        EntityInstance instance = findInstanceOrThrow(id);
        verifyInstanceBelongsToType(instance, typeId);
        return EntityInstanceResponse.from(instance);
    }

    @Override
    public PageResponse<EntityInstanceResponse> listInstances(Long typeId, PageRequest page, TokenClaims claims) {
        findEntityTypeOrThrow(typeId);
        checkPermission(claims, Action.READ, typeId);

        org.springframework.data.domain.Page<EntityInstance> springPage =
                entityInstanceRepository.findAllByEntityTypeId(typeId, page.toSpringPageRequest());
        return PageResponse.from(springPage.map(EntityInstanceResponse::from), page.getPage());
    }

    @Override
    @Transactional
    public EntityInstanceResponse updateInstance(Long typeId, Long id, UpdateInstanceRequest request, TokenClaims claims) {
        findEntityTypeOrThrow(typeId);
        checkPermission(claims, Action.UPDATE, typeId);

        EntityInstance instance = findInstanceOrThrow(id);
        verifyInstanceBelongsToType(instance, typeId);

        if (!request.version().equals(instance.getVersion())) {
            throw new ConflictException("Resource was modified by another request. Please refresh and retry.");
        }

        if (request.name() != null && !request.name().isBlank()) {
            instance.setName(request.name());
        }
        if (request.description() != null) {
            instance.setDescription(request.description());
        }
        instance.setUpdatedAt(Instant.now());
        EntityInstance saved = entityInstanceRepository.saveAndFlush(instance);
        return EntityInstanceResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteInstance(Long typeId, Long id, TokenClaims claims) {
        findEntityTypeOrThrow(typeId);
        checkPermission(claims, Action.DELETE, typeId);

        EntityInstance instance = findInstanceOrThrow(id);
        verifyInstanceBelongsToType(instance, typeId);
        entityInstanceRepository.delete(instance);
    }

    private void checkPermission(TokenClaims claims, Action action, Long typeId) {
        if (!permissionChecker.hasPermission(claims.roles(), action, typeId)) {
            throw new ForbiddenException(
                    "You do not have " + action + " permission on entity type " + typeId + "."
            );
        }
    }

    private EntityType findEntityTypeOrThrow(Long typeId) {
        return entityTypeRepository.findById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("Entity type with id " + typeId + " not found."));
    }

    private EntityInstance findInstanceOrThrow(Long id) {
        return entityInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entity instance with id " + id + " not found."));
    }

    private void verifyInstanceBelongsToType(EntityInstance instance, Long typeId) {
        if (!instance.getEntityType().getId().equals(typeId)) {
            throw new ResourceNotFoundException(
                    "Entity instance " + instance.getId() + " does not belong to entity type " + typeId + "."
            );
        }
    }
}
