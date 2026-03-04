package com.userrole.user;

import com.userrole.auth.spi.TokenClaims;
import com.userrole.common.ApiResponse;
import com.userrole.common.PageRequest;
import com.userrole.common.PageResponse;
import com.userrole.common.RoleConstants;
import com.userrole.role.dto.RoleResponse;
import com.userrole.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long id,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        UserResponse response = userService.getUserById(id, claims.userId(), claims.roles());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> listUsers(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UserFilter filter = new UserFilter(dept, name);
        PageResponse<UserResponse> result = userService.listUsers(filter, new PageRequest(page, size));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        UserResponse response = userService.updateUser(id, request, claims.userId(), claims.roles());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRole(
            @PathVariable Long id,
            @PathVariable Long roleId,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        userService.assignRole(id, roleId, claims.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeRole(
            @PathVariable Long id,
            @PathVariable Long roleId,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        userService.revokeRole(id, roleId, claims.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRolesForUser(
            @PathVariable Long id,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        // Self-access check: only admin or the user themselves
        boolean isAdmin = claims.roles().contains(RoleConstants.ADMIN);
        if (!isAdmin && !id.equals(claims.userId())) {
            throw new com.userrole.common.ForbiddenException("You may only view your own roles.");
        }
        List<RoleResponse> roles = userService.getRolesForUser(id);
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @GetMapping("/{id}/roles/history")
    public ResponseEntity<ApiResponse<PageResponse<UserRoleAuditEntry>>> getRoleChangeHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal TokenClaims claims
    ) {
        boolean isAdmin = claims.roles().contains(RoleConstants.ADMIN);
        if (!isAdmin && !id.equals(claims.userId())) {
            throw new com.userrole.common.ForbiddenException("You may only view your own role history.");
        }
        PageResponse<UserRoleAuditEntry> result = userService.getRoleChangeHistory(id, new PageRequest(page, size));
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
