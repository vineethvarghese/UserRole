package com.userrole.role;

import com.userrole.common.ConflictException;
import com.userrole.common.ResourceNotFoundException;
import com.userrole.common.RoleAssignmentQueryPort;
import com.userrole.role.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleAssignmentQueryPort roleAssignmentQueryPort;

    public RoleServiceImpl(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RoleAssignmentQueryPort roleAssignmentQueryPort
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.roleAssignmentQueryPort = roleAssignmentQueryPort;
    }

    @Override
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new ConflictException("Role with name '" + request.name() + "' already exists.");
        }
        Role role = new Role(request.name(), request.description());
        Role saved = roleRepository.save(role);
        return RoleResponse.from(saved);
    }

    @Override
    public RoleResponse getRoleById(Long id) {
        Role role = findRoleOrThrow(id);
        return RoleResponse.from(role);
    }

    @Override
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, UpdateRoleRequest request) {
        Role role = findRoleOrThrow(id);

        if (!request.version().equals(role.getVersion())) {
            throw new ConflictException("Resource was modified by another request. Please refresh and retry.");
        }

        if (request.name() != null && !request.name().isBlank()) {
            if (!role.getName().equals(request.name()) && roleRepository.existsByName(request.name())) {
                throw new ConflictException("Role with name '" + request.name() + "' already exists.");
            }
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }
        role.setUpdatedAt(Instant.now());
        Role saved = roleRepository.saveAndFlush(role);
        return RoleResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        findRoleOrThrow(id);

        long assignedCount = roleAssignmentQueryPort.countUsersAssignedToRole(id);
        if (assignedCount > 0) {
            throw new ConflictException(
                    "Cannot delete role: " + assignedCount + " user(s) are still assigned to it."
            );
        }
        roleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public PermissionResponse addPermission(Long roleId, Action action, Long entityTypeId) {
        Role role = findRoleOrThrow(roleId);

        if (permissionRepository.existsByRoleIdAndActionAndEntityTypeId(roleId, action, entityTypeId)) {
            throw new ConflictException(
                    "Permission for action " + action + " on entity type " + entityTypeId
                    + " already exists for this role."
            );
        }
        Permission permission = new Permission(role, action, entityTypeId);
        Permission saved = permissionRepository.save(permission);
        return PermissionResponse.from(saved);
    }

    @Override
    @Transactional
    public void removePermission(Long roleId, Long permissionId) {
        findRoleOrThrow(roleId);
        Permission permission = permissionRepository.findById(permissionId)
                .filter(p -> p.getRole().getId().equals(roleId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Permission with id " + permissionId + " not found on role " + roleId
                ));
        permissionRepository.delete(permission);
    }

    @Override
    public List<PermissionResponse> getPermissionsForRole(Long roleId) {
        findRoleOrThrow(roleId);
        return permissionRepository.findAllByRoleId(roleId).stream()
                .map(PermissionResponse::from)
                .toList();
    }

    private Role findRoleOrThrow(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role with id " + id + " not found."));
    }
}
