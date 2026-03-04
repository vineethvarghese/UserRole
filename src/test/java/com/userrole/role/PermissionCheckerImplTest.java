package com.userrole.role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCheckerImplTest {

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionCheckerImpl permissionChecker;

    private Role editorRole;
    private Permission readPermission;

    @BeforeEach
    void setUp() {
        editorRole = new Role("EDITOR", null);
        readPermission = new Permission(editorRole, Action.READ, 10L);
    }

    @Test
    void hasPermission_whenMatchingPermissionExists_returnsTrue() {
        when(permissionRepository.findByRoleNamesAndActionAndEntityTypeId(
                List.of("EDITOR"), Action.READ, 10L))
                .thenReturn(List.of(readPermission));

        assertThat(permissionChecker.hasPermission(List.of("EDITOR"), Action.READ, 10L)).isTrue();
    }

    @Test
    void hasPermission_whenNoMatchingPermission_returnsFalse() {
        when(permissionRepository.findByRoleNamesAndActionAndEntityTypeId(
                List.of("VIEWER"), Action.READ, 10L))
                .thenReturn(List.of());

        assertThat(permissionChecker.hasPermission(List.of("VIEWER"), Action.READ, 10L)).isFalse();
    }

    @Test
    void hasPermission_forCreateAction_returnsTrueWhenGranted() {
        Permission createPerm = new Permission(editorRole, Action.CREATE, 10L);
        when(permissionRepository.findByRoleNamesAndActionAndEntityTypeId(
                List.of("EDITOR"), Action.CREATE, 10L))
                .thenReturn(List.of(createPerm));

        assertThat(permissionChecker.hasPermission(List.of("EDITOR"), Action.CREATE, 10L)).isTrue();
    }

    @Test
    void hasPermission_forUpdateAction_returnsTrueWhenGranted() {
        Permission updatePerm = new Permission(editorRole, Action.UPDATE, 10L);
        when(permissionRepository.findByRoleNamesAndActionAndEntityTypeId(
                List.of("EDITOR"), Action.UPDATE, 10L))
                .thenReturn(List.of(updatePerm));

        assertThat(permissionChecker.hasPermission(List.of("EDITOR"), Action.UPDATE, 10L)).isTrue();
    }

    @Test
    void hasPermission_forDeleteAction_returnsTrueWhenGranted() {
        Permission deletePerm = new Permission(editorRole, Action.DELETE, 10L);
        when(permissionRepository.findByRoleNamesAndActionAndEntityTypeId(
                List.of("EDITOR"), Action.DELETE, 10L))
                .thenReturn(List.of(deletePerm));

        assertThat(permissionChecker.hasPermission(List.of("EDITOR"), Action.DELETE, 10L)).isTrue();
    }

    @Test
    void hasPermission_whenMultipleRolesAndOneMatches_returnsTrue() {
        when(permissionRepository.findByRoleNamesAndActionAndEntityTypeId(
                List.of("VIEWER", "EDITOR"), Action.READ, 10L))
                .thenReturn(List.of(readPermission)); // EDITOR matches

        assertThat(permissionChecker.hasPermission(List.of("VIEWER", "EDITOR"), Action.READ, 10L)).isTrue();
    }

    @Test
    void hasPermission_whenMultipleRolesAndNoneMatch_returnsFalse() {
        when(permissionRepository.findByRoleNamesAndActionAndEntityTypeId(
                List.of("VIEWER", "REPORTER"), Action.DELETE, 10L))
                .thenReturn(List.of());

        assertThat(permissionChecker.hasPermission(List.of("VIEWER", "REPORTER"), Action.DELETE, 10L)).isFalse();
    }

    @Test
    void hasPermission_whenRoleListIsEmpty_returnsFalse() {
        assertThat(permissionChecker.hasPermission(List.of(), Action.READ, 10L)).isFalse();
    }

    @Test
    void hasPermission_whenRoleListIsNull_returnsFalse() {
        assertThat(permissionChecker.hasPermission(null, Action.READ, 10L)).isFalse();
    }
}
