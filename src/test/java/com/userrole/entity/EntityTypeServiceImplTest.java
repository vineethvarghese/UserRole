package com.userrole.entity;

import com.userrole.common.ConflictException;
import com.userrole.common.ResourceNotFoundException;
import com.userrole.entity.dto.CreateEntityTypeRequest;
import com.userrole.entity.dto.EntityTypeResponse;
import com.userrole.entity.dto.UpdateEntityTypeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityTypeServiceImplTest {

    @Mock
    private EntityTypeRepository entityTypeRepository;

    @Mock
    private EntityInstanceRepository entityInstanceRepository;

    @InjectMocks
    private EntityTypeServiceImpl entityTypeService;

    private EntityType sampleEntityType;

    @BeforeEach
    void setUp() {
        sampleEntityType = new EntityType("Invoice", "Invoice documents");
        setId(sampleEntityType, 1L);
    }

    @Test
    void createEntityType_whenNameIsUnique_returnsEntityTypeResponse() {
        when(entityTypeRepository.existsByName("Invoice")).thenReturn(false);
        when(entityTypeRepository.save(any(EntityType.class))).thenReturn(sampleEntityType);

        EntityTypeResponse result = entityTypeService.createEntityType(new CreateEntityTypeRequest("Invoice", "Invoice documents"));

        assertThat(result.name()).isEqualTo("Invoice");
    }

    @Test
    void createEntityType_whenNameAlreadyExists_throwsConflictException() {
        when(entityTypeRepository.existsByName("Invoice")).thenReturn(true);

        assertThatThrownBy(() -> entityTypeService.createEntityType(new CreateEntityTypeRequest("Invoice", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invoice");
    }

    @Test
    void getEntityTypeById_whenEntityTypeExists_returnsResponse() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleEntityType));

        EntityTypeResponse result = entityTypeService.getEntityTypeById(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void getEntityTypeById_whenEntityTypeNotFound_throwsResourceNotFoundException() {
        when(entityTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entityTypeService.getEntityTypeById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteEntityType_whenNoInstancesExist_deletesSuccessfully() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleEntityType));
        when(entityInstanceRepository.countByEntityTypeId(1L)).thenReturn(0L);

        entityTypeService.deleteEntityType(1L);

        verify(entityTypeRepository).deleteById(1L);
    }

    @Test
    void deleteEntityType_whenInstancesExist_throwsConflictException() {
        when(entityTypeRepository.findById(1L)).thenReturn(Optional.of(sampleEntityType));
        when(entityInstanceRepository.countByEntityTypeId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> entityTypeService.deleteEntityType(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("5");

        verify(entityTypeRepository, never()).deleteById(any());
    }

    private void setId(EntityType entityType, Long id) {
        try {
            var field = EntityType.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entityType, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
