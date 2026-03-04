package com.userrole.role;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PermissionCheckerImpl implements PermissionChecker {

    private final PermissionRepository permissionRepository;

    public PermissionCheckerImpl(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    public boolean hasPermission(List<String> roleNames, Action action, Long entityTypeId) {
        if (roleNames == null || roleNames.isEmpty()) {
            return false;
        }
        List<Permission> matches = permissionRepository.findByRoleNamesAndActionAndEntityTypeId(
                roleNames, action, entityTypeId
        );
        return !matches.isEmpty();
    }
}
