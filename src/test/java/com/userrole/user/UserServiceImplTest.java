package com.userrole.user;

import com.userrole.auth.spi.TokenRevocationPort;
import com.userrole.common.*;
import com.userrole.role.Role;
import com.userrole.role.RoleRepository;
import com.userrole.user.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserRoleAuditLogRepository auditLogRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private TokenRevocationPort tokenRevocationPort;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;
    private Role sampleRole;

    @BeforeEach
    void setUp() {
        sampleUser = new User("alice", "hashed_pw", "Alice Smith", "Engineering", "alice@example.com", "555-1234");
        setUserId(sampleUser, 1L);
        setUserVersion(sampleUser, 0L);

        sampleRole = new Role("EDITOR", "Editor role");
        setRoleId(sampleRole, 10L);
    }

    // ---- createUser ----

    @Test
    void createUser_whenCredentialsAreUnique_returnsUserResponseWithoutPasswordHash() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain_pw")).thenReturn("hashed_pw");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        CreateUserRequest request = new CreateUserRequest("alice", "plain_pw", "Alice Smith", "Engineering", "alice@example.com", "555-1234");
        UserResponse result = userService.createUser(request);

        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.id()).isEqualTo(1L);
        // passwordHash must not be in the response record
        assertThat(UserResponse.class.getRecordComponents())
                .noneMatch(c -> c.getName().equals("passwordHash"));
    }

    @Test
    void createUser_whenUsernameAlreadyExists_throwsConflictException() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("alice", "pw", "Alice", null, "a@b.com", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice");
    }

    // ---- getUserById ----

    @Test
    void getUserById_whenAdminRequestsAnyUser_returnsUserResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserResponse result = userService.getUserById(1L, 99L, List.of(RoleConstants.ADMIN));

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void getUserById_whenUserRequestsOwnProfile_returnsUserResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserResponse result = userService.getUserById(1L, 1L, List.of("EDITOR"));

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void getUserById_whenNonAdminRequestsAnotherUsersProfile_throwsForbiddenException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> userService.getUserById(1L, 2L, List.of("EDITOR")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getUserById_whenUserNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L, 99L, List.of(RoleConstants.ADMIN)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---- listUsers ----

    @Test
    void listUsers_returnsPagedResults() {
        when(userRepository.findAllByFilter(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleUser)));

        PageResponse<UserResponse> result = userService.listUsers(new UserFilter(null, null), new PageRequest(0, 20));

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).username()).isEqualTo("alice");
    }

    // ---- updateUser ----

    @Test
    void updateUser_whenAdminUpdatesAnyUser_updatesSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(sampleUser);

        userService.updateUser(1L,
                new UpdateUserRequest("Alice Updated", null, null, null, 0L),
                99L, List.of(RoleConstants.ADMIN));

        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void updateUser_whenNonAdminUpdatesOtherUser_throwsForbiddenException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> userService.updateUser(1L,
                new UpdateUserRequest("Name", null, null, null, 0L),
                2L, List.of("EDITOR")))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---- deleteUser ----

    @Test
    void deleteUser_setsActiveToFalseAndRevokesTokens() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        userService.deleteUser(1L);

        assertThat(sampleUser.isActive()).isFalse();
        verify(tokenRevocationPort).revokeAllTokensForUser(1L);
    }

    // ---- assignRole ----

    @Test
    void assignRole_whenNotAlreadyAssigned_savesUserRoleAndAuditEntry() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(sampleRole));
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L)).thenReturn(false);
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(i -> i.getArgument(0));
        when(auditLogRepository.save(any(UserRoleAuditLog.class))).thenAnswer(i -> i.getArgument(0));

        userService.assignRole(1L, 10L, 99L);

        verify(userRoleRepository).save(any(UserRole.class));
        verify(auditLogRepository).save(argThat(log ->
                log.getAction() == AuditAction.ASSIGNED && log.getUserId().equals(1L)));
    }

    @Test
    void assignRole_whenAlreadyAssigned_throwsConflictException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(sampleRole));
        when(userRoleRepository.existsByUserIdAndRoleId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> userService.assignRole(1L, 10L, 99L))
                .isInstanceOf(ConflictException.class);
    }

    // ---- revokeRole ----

    @Test
    void revokeRole_whenRoleIsAssigned_deletesAndWritesAuditEntry() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(sampleRole));
        UserRoleId pk = new UserRoleId(1L, 10L);
        when(userRoleRepository.existsById(pk)).thenReturn(true);
        when(auditLogRepository.save(any(UserRoleAuditLog.class))).thenAnswer(i -> i.getArgument(0));

        userService.revokeRole(1L, 10L, 99L);

        verify(userRoleRepository).deleteById(pk);
        verify(auditLogRepository).save(argThat(log ->
                log.getAction() == AuditAction.REVOKED && log.getUserId().equals(1L)));
    }

    @Test
    void revokeRole_whenRoleNotAssigned_throwsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(roleRepository.findById(10L)).thenReturn(Optional.of(sampleRole));
        when(userRoleRepository.existsById(new UserRoleId(1L, 10L))).thenReturn(false);

        assertThatThrownBy(() -> userService.revokeRole(1L, 10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- getRoleChangeHistory ----

    @Test
    void getRoleChangeHistory_returnsPaginatedAuditEntries() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        UserRoleAuditLog log = new UserRoleAuditLog(1L, 10L, "EDITOR", AuditAction.ASSIGNED, 99L);
        when(auditLogRepository.findAllByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        PageResponse<UserRoleAuditEntry> result = userService.getRoleChangeHistory(1L, new PageRequest(0, 20));

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).action()).isEqualTo(AuditAction.ASSIGNED);
    }

    // ---- countUsersAssignedToRole (RoleAssignmentQueryPort) ----

    @Test
    void countUsersAssignedToRole_delegatesToRepository() {
        when(userRoleRepository.countByRoleId(10L)).thenReturn(5L);

        long count = userService.countUsersAssignedToRole(10L);

        assertThat(count).isEqualTo(5L);
    }

    // ---- helpers ----

    private void setUserId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setUserVersion(User user, Long version) {
        try {
            var field = User.class.getDeclaredField("version");
            field.setAccessible(true);
            field.set(user, version);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setRoleId(Role role, Long id) {
        try {
            var field = Role.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(role, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
