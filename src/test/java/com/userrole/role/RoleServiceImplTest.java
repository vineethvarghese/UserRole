package com.userrole.role;

import com.userrole.common.ConflictException;
import com.userrole.common.ResourceNotFoundException;
import com.userrole.common.RoleAssignmentQueryPort;
import com.userrole.role.dto.CreateRoleRequest;
import com.userrole.role.dto.PermissionResponse;
import com.userrole.role.dto.RoleResponse;
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
@SuppressWarnings("null")
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleAssignmentQueryPort roleAssignmentQueryPort;

    @InjectMocks
    private RoleServiceImpl roleService;

    private Role sampleRole;

    @BeforeEach
    void setUp() {
        sampleRole = new Role("EDITOR", "Editor role");
        // Use reflection to set generated id for tests
        setId(sampleRole, 1L);
    }

    // ---- createRole ----

    @Test
    void createRole_whenNameIsUnique_returnsRoleResponse() {
        when(roleRepository.existsByName("EDITOR")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenReturn(sampleRole);

        RoleResponse result = roleService.createRole(new CreateRoleRequest("EDITOR", "Editor role"));

        assertThat(result.name()).isEqualTo("EDITOR");
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void createRole_whenNameAlreadyExists_throwsConflictException() {
        when(roleRepository.existsByName("EDITOR")).thenReturn(true);

        assertThatThrownBy(() -> roleService.createRole(new CreateRoleRequest("EDITOR", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EDITOR");
    }

    // ---- getRoleById ----

    @Test
    void getRoleById_whenRoleExists_returnsRoleResponse() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(sampleRole));

        RoleResponse result = roleService.getRoleById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("EDITOR");
    }

    @Test
    void getRoleById_whenRoleNotFound_throwsResourceNotFoundException() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getRoleById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---- deleteRole ----

    @Test
    void deleteRole_whenNoUsersAssigned_deletesSuccessfully() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(sampleRole));
        when(roleAssignmentQueryPort.countUsersAssignedToRole(1L)).thenReturn(0L);

        roleService.deleteRole(1L);

        verify(roleRepository).deleteById(1L);
    }

    @Test
    void deleteRole_whenUsersAreAssigned_throwsConflictException() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(sampleRole));
        when(roleAssignmentQueryPort.countUsersAssignedToRole(1L)).thenReturn(3L);

        assertThatThrownBy(() -> roleService.deleteRole(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3");

        verify(roleRepository, never()).deleteById(any());
    }

    // ---- addPermission ----

    @Test
    void addPermission_whenPermissionDoesNotExist_savesAndReturnsResponse() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(sampleRole));
        when(permissionRepository.existsByRoleIdAndActionAndEntityTypeId(1L, Action.READ, 10L)).thenReturn(false);

        Permission savedPermission = new Permission(sampleRole, Action.READ, 10L);
        setPermissionId(savedPermission, 5L);
        when(permissionRepository.save(any(Permission.class))).thenReturn(savedPermission);

        PermissionResponse result = roleService.addPermission(1L, Action.READ, 10L);

        assertThat(result.action()).isEqualTo(Action.READ);
        assertThat(result.entityTypeId()).isEqualTo(10L);
    }

    @Test
    void addPermission_whenDuplicatePermission_throwsConflictException() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(sampleRole));
        when(permissionRepository.existsByRoleIdAndActionAndEntityTypeId(1L, Action.READ, 10L)).thenReturn(true);

        assertThatThrownBy(() -> roleService.addPermission(1L, Action.READ, 10L))
                .isInstanceOf(ConflictException.class);
    }

    // ---- removePermission ----

    @Test
    void removePermission_whenPermissionExists_deletesSuccessfully() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(sampleRole));
        Permission perm = new Permission(sampleRole, Action.DELETE, 10L);
        setPermissionId(perm, 7L);
        when(permissionRepository.findById(7L)).thenReturn(Optional.of(perm));

        roleService.removePermission(1L, 7L);

        verify(permissionRepository).delete(perm);
    }

    @Test
    void removePermission_whenPermissionNotFound_throwsResourceNotFoundException() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(sampleRole));
        when(permissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.removePermission(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- helpers ----

    private void setId(Role role, Long id) {
        try {
            var field = Role.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(role, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setPermissionId(Permission permission, Long id) {
        try {
            var field = Permission.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(permission, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
