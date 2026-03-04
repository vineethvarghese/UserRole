package com.userrole.user;

import com.userrole.auth.spi.TokenRevocationPort;
import com.userrole.common.*;
import com.userrole.role.RoleRepository;
import com.userrole.role.dto.RoleResponse;
import com.userrole.user.dto.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService, RoleAssignmentQueryPort {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRoleAuditLogRepository auditLogRepository;
    private final RoleRepository roleRepository;
    private final TokenRevocationPort tokenRevocationPort;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            UserRoleAuditLogRepository auditLogRepository,
            RoleRepository roleRepository,
            @Lazy TokenRevocationPort tokenRevocationPort,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditLogRepository = auditLogRepository;
        this.roleRepository = roleRepository;
        this.tokenRevocationPort = tokenRevocationPort;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username '" + request.username() + "' is already taken.");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email '" + request.email() + "' is already registered.");
        }
        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(
                request.username(),
                hashedPassword,
                request.fullName(),
                request.department(),
                request.email(),
                request.phone()
        );
        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    @Override
    public UserResponse getUserById(Long id, Long requestingUserId, List<String> requestingUserRoles) {
        User user = findActiveUserOrThrow(id);
        boolean isAdmin = requestingUserRoles.contains(RoleConstants.ADMIN);
        if (!isAdmin && !id.equals(requestingUserId)) {
            throw new ForbiddenException("You may only view your own profile.");
        }
        return UserResponse.from(user);
    }

    @Override
    public PageResponse<UserResponse> listUsers(UserFilter filter, PageRequest page) {
        org.springframework.data.domain.Page<User> springPage = userRepository.findAllByFilter(
                filter.department(),
                filter.name(),
                page.toSpringPageRequest()
        );
        return PageResponse.from(springPage.map(UserResponse::from), page.getPage());
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request, Long requestingUserId, List<String> requestingUserRoles) {
        User user = findActiveUserOrThrow(id);
        boolean isAdmin = requestingUserRoles.contains(RoleConstants.ADMIN);
        if (!isAdmin && !id.equals(requestingUserId)) {
            throw new ForbiddenException("You may only update your own profile.");
        }

        if (!request.version().equals(user.getVersion())) {
            throw new ConflictException("Resource was modified by another request. Please refresh and retry.");
        }

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName());
        }
        if (request.department() != null) {
            user.setDepartment(request.department());
        }
        if (request.email() != null && !request.email().isBlank()) {
            if (!user.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
                throw new ConflictException("Email '" + request.email() + "' is already registered.");
            }
            user.setEmail(request.email());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.saveAndFlush(user);
        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = findActiveUserOrThrow(id);
        user.setActive(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        // Revoke all tokens atomically in the same transaction
        tokenRevocationPort.revokeAllTokensForUser(id);
    }

    @Override
    @Transactional
    public void assignRole(Long userId, Long roleId, Long performedBy) {
        findActiveUserOrThrow(userId);
        com.userrole.role.Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role with id " + roleId + " not found."));

        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new ConflictException("User " + userId + " is already assigned role " + roleId + ".");
        }

        userRoleRepository.save(new UserRole(userId, roleId));
        auditLogRepository.save(new UserRoleAuditLog(userId, roleId, role.getName(), AuditAction.ASSIGNED, performedBy));
    }

    @Override
    @Transactional
    public void revokeRole(Long userId, Long roleId, Long performedBy) {
        findActiveUserOrThrow(userId);
        com.userrole.role.Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role with id " + roleId + " not found."));

        UserRoleId pk = new UserRoleId(userId, roleId);
        if (!userRoleRepository.existsById(pk)) {
            throw new ResourceNotFoundException(
                    "User " + userId + " does not have role " + roleId + " assigned."
            );
        }

        userRoleRepository.deleteById(pk);
        auditLogRepository.save(new UserRoleAuditLog(userId, roleId, role.getName(), AuditAction.REVOKED, performedBy));
    }

    @Override
    public List<RoleResponse> getRolesForUser(Long userId) {
        findActiveUserOrThrow(userId);
        List<UserRole> userRoles = userRoleRepository.findAllByUserId(userId);
        return userRoles.stream()
                .map(ur -> roleRepository.findById(ur.getRoleId()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(RoleResponse::from)
                .toList();
    }

    @Override
    public Set<Long> getRoleIdsForUser(Long userId) {
        return userRoleRepository.findAllByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toSet());
    }

    @Override
    public PageResponse<com.userrole.user.dto.UserRoleAuditEntry> getRoleChangeHistory(Long userId, PageRequest page) {
        findActiveUserOrThrow(userId);
        org.springframework.data.domain.Page<UserRoleAuditLog> springPage =
                auditLogRepository.findAllByUserId(userId, page.toSpringPageRequest());
        return PageResponse.from(
                springPage.map(com.userrole.user.dto.UserRoleAuditEntry::from),
                page.getPage()
        );
    }

    // ---- UserService internal lookup methods (used by auth.local) ----

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found."));
    }

    @Override
    public User findById(Long id) {
        return findActiveUserOrThrow(id);
    }

    // ---- RoleAssignmentQueryPort implementation ----

    @Override
    public long countUsersAssignedToRole(Long roleId) {
        return userRoleRepository.countByRoleId(roleId);
    }

    // ---- helpers ----

    private User findActiveUserOrThrow(Long id) {
        return userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found."));
    }
}
