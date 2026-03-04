package com.userrole.entity;

import com.userrole.common.ApiResponse;
import com.userrole.entity.dto.CreateEntityTypeRequest;
import com.userrole.entity.dto.EntityTypeResponse;
import com.userrole.entity.dto.UpdateEntityTypeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entity-types")
public class EntityTypeController {

    private final EntityTypeService entityTypeService;

    public EntityTypeController(EntityTypeService entityTypeService) {
        this.entityTypeService = entityTypeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EntityTypeResponse>> createEntityType(
            @Valid @RequestBody CreateEntityTypeRequest request
    ) {
        EntityTypeResponse response = entityTypeService.createEntityType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EntityTypeResponse>>> listEntityTypes() {
        return ResponseEntity.ok(ApiResponse.success(entityTypeService.listEntityTypes()));
    }

    @GetMapping("/{typeId}")
    public ResponseEntity<ApiResponse<EntityTypeResponse>> getEntityTypeById(@PathVariable Long typeId) {
        return ResponseEntity.ok(ApiResponse.success(entityTypeService.getEntityTypeById(typeId)));
    }

    @PutMapping("/{typeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EntityTypeResponse>> updateEntityType(
            @PathVariable Long typeId,
            @Valid @RequestBody UpdateEntityTypeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(entityTypeService.updateEntityType(typeId, request)));
    }

    @DeleteMapping("/{typeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEntityType(@PathVariable Long typeId) {
        entityTypeService.deleteEntityType(typeId);
        return ResponseEntity.noContent().build();
    }
}
