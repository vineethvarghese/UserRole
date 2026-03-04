package com.userrole.entity;

import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.ApiResponse;
import com.userrole.common.PageRequest;
import com.userrole.common.PageResponse;
import com.userrole.entity.dto.CreateInstanceRequest;
import com.userrole.entity.dto.EntityInstanceResponse;
import com.userrole.entity.dto.UpdateInstanceRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/entity-types/{typeId}/instances")
public class EntityInstanceController {

    private final EntityInstanceService entityInstanceService;

    public EntityInstanceController(EntityInstanceService entityInstanceService) {
        this.entityInstanceService = entityInstanceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EntityInstanceResponse>> createInstance(
            @PathVariable Long typeId,
            @Valid @RequestBody CreateInstanceRequest request,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        EntityInstanceResponse response = entityInstanceService.createInstance(typeId, request, claims);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EntityInstanceResponse>>> listInstances(
            @PathVariable Long typeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        PageResponse<EntityInstanceResponse> result =
                entityInstanceService.listInstances(typeId, new PageRequest(page, size), claims);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EntityInstanceResponse>> getInstanceById(
            @PathVariable Long typeId,
            @PathVariable Long id,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        EntityInstanceResponse response = entityInstanceService.getInstanceById(typeId, id, claims);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EntityInstanceResponse>> updateInstance(
            @PathVariable Long typeId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateInstanceRequest request,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        EntityInstanceResponse response = entityInstanceService.updateInstance(typeId, id, request, claims);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstance(
            @PathVariable Long typeId,
            @PathVariable Long id,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        entityInstanceService.deleteInstance(typeId, id, claims);
        return ResponseEntity.noContent().build();
    }
}
