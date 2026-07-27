package com.aiworkmate.service.impl;

import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class UserAccessServiceImpl implements UserAccessService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserMapper userMapper;
    private final AccessControlMapper accessControlMapper;

    @Override
    @Transactional(readOnly = true)
    public ResolvedUserAccess resolveActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return null;
        }
        List<String> roles = accessControlMapper.selectUserRoleCodes(user.getTenantId(), user.getId());
        if (roles.isEmpty()) {
            roles = List.of(user.getRole());
        }
        String primaryRole = roles.stream()
                .min(Comparator.comparingInt(this::rolePriority))
                .orElse(user.getRole());
        return new ResolvedUserAccess(
                user.getId(),
                user.getEmail(),
                user.getTenantId(),
                primaryRole,
                List.copyOf(roles),
                permissionsForRoles(user.getTenantId(), roles),
                List.copyOf(accessControlMapper.selectDataScopes(user.getTenantId(), roles)),
                user.getPermissionVersion()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> permissionsForRole(String roleCode) {
        if (SUPER_ADMIN.equals(roleCode)) {
            return List.copyOf(accessControlMapper.selectAllPermissionCodes());
        }
        return List.copyOf(accessControlMapper.selectPermissionCodes(roleCode));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> permissionsForRoles(Long tenantId, List<String> roleCodes) {
        if (roleCodes.contains(SUPER_ADMIN)) {
            return List.copyOf(accessControlMapper.selectAllPermissionCodesForTenant(tenantId));
        }
        return roleCodes.isEmpty()
                ? List.of()
                : List.copyOf(accessControlMapper.selectPermissionCodesForRoles(tenantId, roleCodes));
    }

    private int rolePriority(String roleCode) {
        return switch (roleCode) {
            case "SUPER_ADMIN" -> 0;
            case "SYSTEM_ADMIN" -> 1;
            case "PROCESS_ADMIN" -> 2;
            case "FINANCE_ADMIN" -> 3;
            default -> 10;
        };
    }
}
