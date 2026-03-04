package com.userrole.entity;

import com.userrole.common.ConflictException;
import com.userrole.common.ResourceNotFoundException;
import com.userrole.entity.dto.CreateEntityTypeRequest;
import com.userrole.entity.dto.EntityTypeResponse;
import com.userrole.entity.dto.UpdateEntityTypeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EntityTypeServiceImpl implements EntityTypeService {

    private final EntityTypeRepository entityTypeRepository;
    private final EntityInstanceRepository entityInstanceRepository;

    public EntityTypeServiceImpl(
            EntityTypeRepository entityTypeRepository,
            EntityInstanceRepository entityInstanceRepository
    ) {
        this.entityTypeRepository = entityTypeRepository;
        this.entityInstanceRepository = entityInstanceRepository;
    }

    @Override
    @Transactional
    public EntityTypeResponse createEntityType(CreateEntityTypeRequest request) {
        if (entityTypeRepository.existsByName(request.name())) {
            throw new ConflictException("Entity type with name '" + request.name() + "' already exists.");
        }
        EntityType entityType = new EntityType(request.name(), request.description());
        EntityType saved = entityTypeRepository.save(entityType);
        return EntityTypeResponse.from(saved);
    }

    @Override
    public EntityTypeResponse getEntityTypeById(Long id) {
        return EntityTypeResponse.from(findEntityTypeOrThrow(id));
    }

    @Override
    public List<EntityTypeResponse> listEntityTypes() {
        return entityTypeRepository.findAll().stream()
                .map(EntityTypeResponse::from)
                .toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public EntityTypeResponse updateEntityType(Long id, UpdateEntityTypeRequest request) {
        EntityType entityType = findEntityTypeOrThrow(id);

        if (!request.version().equals(entityType.getVersion())) {
            throw new ConflictException("Resource was modified by another request. Please refresh and retry.");
        }

        if (request.name() != null && !request.name().isBlank()) {
            if (!entityType.getName().equals(request.name()) && entityTypeRepository.existsByName(request.name())) {
                throw new ConflictException("Entity type with name '" + request.name() + "' already exists.");
            }
            entityType.setName(request.name());
        }
        if (request.description() != null) {
            entityType.setDescription(request.description());
        }
        entityType.setUpdatedAt(Instant.now());

        EntityType saved = entityTypeRepository.saveAndFlush(entityType);
        return EntityTypeResponse.from(saved);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void deleteEntityType(Long id) {
        findEntityTypeOrThrow(id);

        long instanceCount = entityInstanceRepository.countByEntityTypeId(id);
        if (instanceCount > 0) {
            throw new ConflictException(
                    "Cannot delete entity type: " + instanceCount + " instance(s) still exist."
            );
        }
        entityTypeRepository.deleteById(id);
    }

    @SuppressWarnings("null")
    private EntityType findEntityTypeOrThrow(Long id) {
        return entityTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entity type with id " + id + " not found."));
    }
}
