package com.userrole.entity;

import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.ForbiddenException;
import com.userrole.common.PageRequest;
import com.userrole.common.PageResponse;
import com.userrole.common.ResourceNotFoundException;
import com.userrole.entity.dto.CreateInstanceRequest;
import com.userrole.entity.dto.EntityInstanceResponse;
import com.userrole.entity.dto.UpdateInstanceRequest;
import com.userrole.role.Action;
import com.userrole.role.PermissionChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityInstanceServiceImplTest {

    @Mock
    private EntityInstanceRepository entityInstanceRepository;

    @Mock
    private EntityTypeRepository entityTypeRepository;

    @Mock
    private PermissionChecker permissionChecker;

    @InjectMocks
    private EntityInstanceServiceImpl entityInstanceService;

    private EntityType sampleType;
    private EntityInstance sampleInstance;
    private TokenClaims claimsWithPermission;
    private TokenClaims claimsWithoutPermission;

    @BeforeEach
    void setUp() {
        sampleType = new EntityType("Invoice", "Invoice docs");
        setEntityTypeId(sampleType, 1L);

        sampleInstance = new EntityInstance(sampleType, "Invoice-001", "First invoice", 10L);
        setInstanceId(sampleInstance, 100L);
        setInstanceVersion(sampleInstance, 0L);

        claimsWithPermission = new TokenClaims(10L, "alice", List.of("EDITOR"),
                Instant.now(), Instant.now().plusSeconds(900));
        claimsWithoutPermission = new TokenClaims(20L, "bob", List.of("VIEWER"),
                Instant.now(), Instant.now().plusSeconds(900));
    }

    // ---- createInstance ----

    @Test
    void createInstance_whenCallerHasCreatePermission_createsAndReturnsInstance() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("EDITOR"), Action.CREATE, 1L)).thenReturn(true);
        when(entityInstanceRepository.save(any(EntityInstance.class))).thenReturn(sampleInstance);

        EntityInstanceResponse result = entityInstanceService.createInstance(
                1L, new CreateInstanceRequest("Invoice-001", "First invoice"), claimsWithPermission);

        assertThat(result.name()).isEqualTo("Invoice-001");
        assertThat(result.createdBy()).isEqualTo(10L);
    }

    @Test
    void createInstance_whenCallerLacksCreatePermission_throwsForbiddenException() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("VIEWER"), Action.CREATE, 1L)).thenReturn(false);

        assertThatThrownBy(() -> entityInstanceService.createInstance(
                1L, new CreateInstanceRequest("Invoice-001", null), claimsWithoutPermission))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createInstance_whenEntityTypeNotFound_throwsResourceNotFoundException() {
        when(entityTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entityInstanceService.createInstance(
                99L, new CreateInstanceRequest("X", null), claimsWithPermission))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- getInstanceById ----

    @Test
    void getInstanceById_whenCallerHasReadPermission_returnsInstance() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("EDITOR"), Action.READ, 1L)).thenReturn(true);
        when(entityInstanceRepository.findById(100L)).thenReturn(Optional.of(sampleInstance));

        EntityInstanceResponse result = entityInstanceService.getInstanceById(1L, 100L, claimsWithPermission);

        assertThat(result.id()).isEqualTo(100L);
    }

    @Test
    void getInstanceById_whenCallerLacksReadPermission_throwsForbiddenException() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("VIEWER"), Action.READ, 1L)).thenReturn(false);

        assertThatThrownBy(() -> entityInstanceService.getInstanceById(1L, 100L, claimsWithoutPermission))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getInstanceById_whenInstanceNotFound_throwsResourceNotFoundException() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("EDITOR"), Action.READ, 1L)).thenReturn(true);
        when(entityInstanceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entityInstanceService.getInstanceById(1L, 999L, claimsWithPermission))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getInstanceById_whenInstanceBelongsToDifferentType_throwsResourceNotFoundException() {
        EntityType otherType = new EntityType("Contract", "Contracts");
        setEntityTypeId(otherType, 99L);
        EntityInstance wrongTypeInstance = new EntityInstance(otherType, "C-001", null, 10L);
        setInstanceId(wrongTypeInstance, 200L);

        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("EDITOR"), Action.READ, 1L)).thenReturn(true);
        when(entityInstanceRepository.findById(200L)).thenReturn(Optional.of(wrongTypeInstance));

        assertThatThrownBy(() -> entityInstanceService.getInstanceById(1L, 200L, claimsWithPermission))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- listInstances ----

    @Test
    void listInstances_whenCallerHasReadPermission_returnsPaginatedList() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("EDITOR"), Action.READ, 1L)).thenReturn(true);
        when(entityInstanceRepository.findAllByEntityTypeId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleInstance)));

        PageResponse<EntityInstanceResponse> result =
                entityInstanceService.listInstances(1L, new PageRequest(0, 20), claimsWithPermission);

        assertThat(result.getData()).hasSize(1);
    }

    @Test
    void listInstances_whenCallerLacksReadPermission_throwsForbiddenException() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("VIEWER"), Action.READ, 1L)).thenReturn(false);

        assertThatThrownBy(() -> entityInstanceService.listInstances(1L, new PageRequest(0, 20), claimsWithoutPermission))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- updateInstance ----

    @Test
    void updateInstance_whenCallerHasUpdatePermission_updatesSuccessfully() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("EDITOR"), Action.UPDATE, 1L)).thenReturn(true);
        when(entityInstanceRepository.findById(100L)).thenReturn(Optional.of(sampleInstance));
        when(entityInstanceRepository.saveAndFlush(any(EntityInstance.class))).thenReturn(sampleInstance);

        EntityInstanceResponse result = entityInstanceService.updateInstance(
                1L, 100L, new UpdateInstanceRequest("Updated Name", null, 0L), claimsWithPermission);

        verify(entityInstanceRepository).saveAndFlush(any(EntityInstance.class));
    }

    @Test
    void updateInstance_whenCallerLacksUpdatePermission_throwsForbiddenException() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("VIEWER"), Action.UPDATE, 1L)).thenReturn(false);

        assertThatThrownBy(() -> entityInstanceService.updateInstance(
                1L, 100L, new UpdateInstanceRequest("X", null, 0L), claimsWithoutPermission))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- deleteInstance ----

    @Test
    void deleteInstance_whenCallerHasDeletePermission_deletesSuccessfully() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("EDITOR"), Action.DELETE, 1L)).thenReturn(true);
        when(entityInstanceRepository.findById(100L)).thenReturn(Optional.of(sampleInstance));

        entityInstanceService.deleteInstance(1L, 100L, claimsWithPermission);

        verify(entityInstanceRepository).delete(sampleInstance);
    }

    @Test
    void deleteInstance_whenCallerLacksDeletePermission_throwsForbiddenException() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleType));
        when(permissionChecker.hasPermission(List.of("VIEWER"), Action.DELETE, 1L)).thenReturn(false);

        assertThatThrownBy(() -> entityInstanceService.deleteInstance(1L, 100L, claimsWithoutPermission))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- helpers ----

    private void setEntityTypeId(EntityType et, Long id) {
        try {
            var field = EntityType.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(et, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setInstanceId(EntityInstance ei, Long id) {
        try {
            var field = EntityInstance.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(ei, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setInstanceVersion(EntityInstance ei, Long version) {
        try {
            var field = EntityInstance.class.getDeclaredField("version");
            field.setAccessible(true);
            field.set(ei, version);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
