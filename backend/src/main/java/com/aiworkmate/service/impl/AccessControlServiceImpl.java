package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AccessControlOverviewResponse;
import com.aiworkmate.dto.AccessPermissionResponse;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.dto.AccessUserResponse;
import com.aiworkmate.dto.SaveRouteRequest;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessControlServiceImpl implements AccessControlService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AccessControlMapper accessControlMapper;

    @Override
    @Transactional(readOnly = true)
    public AccessControlOverviewResponse overview() {
        List<AccessPermissionResponse> permissions = accessControlMapper.selectPermissions();
        List<String> allPermissionCodes = permissions.stream().map(AccessPermissionResponse::code).toList();
        List<AccessRoleResponse> roles = accessControlMapper.selectRoles().stream()
                .map(role -> role.withPermissions(SUPER_ADMIN.equals(role.code())
                        ? allPermissionCodes
                        : accessControlMapper.selectPermissionCodes(role.code())))
                .toList();
        return new AccessControlOverviewResponse(
                List.copyOf(accessControlMapper.selectUsers()),
                roles,
                List.copyOf(permissions),
                List.copyOf(accessControlMapper.selectRoutes())
        );
    }

    @Override
    @Transactional
    public AccessUserResponse assignRole(Long operatorUserId, Long userId, String roleCode) {
        String normalizedRole = roleCode.trim().toUpperCase();
        assertRoleExists(normalizedRole);
        String previousRole = accessControlMapper.selectUserRole(userId);
        if (previousRole == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "用户不存在");
        }
        if (SUPER_ADMIN.equals(previousRole)
                && !SUPER_ADMIN.equals(normalizedRole)
                && accessControlMapper.countActiveSuperAdmins() <= 1) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "必须至少保留一名超级管理员");
        }
        accessControlMapper.updateUserRole(userId, normalizedRole);
        accessControlMapper.insertAudit(operatorUserId, "ASSIGN_USER_ROLE", "USER",
                String.valueOf(userId), previousRole, normalizedRole);
        log.info("User role assigned, operatorUserId={}, userId={}, role={}",
                operatorUserId, userId, normalizedRole);
        return accessControlMapper.selectUsers().stream()
                .filter(user -> user.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
    }

    @Override
    @Transactional
    public AccessRoleResponse updateRolePermissions(Long operatorUserId,
                                                    String roleCode,
                                                    Set<String> permissionCodes) {
        String normalizedRole = roleCode.trim().toUpperCase();
        assertRoleExists(normalizedRole);
        if (SUPER_ADMIN.equals(normalizedRole)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "超级管理员始终拥有全部权限");
        }

        Set<String> requested = Set.copyOf(permissionCodes);
        Set<String> available = Set.copyOf(accessControlMapper.selectAllPermissionCodes());
        if (!available.containsAll(requested)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "包含不存在的权限编码");
        }

        List<String> previous = accessControlMapper.selectPermissionCodes(normalizedRole);
        accessControlMapper.deleteRolePermissions(normalizedRole);
        if (!requested.isEmpty()) {
            accessControlMapper.insertRolePermissions(normalizedRole, requested);
        }
        accessControlMapper.insertAudit(operatorUserId, "UPDATE_ROLE_PERMISSIONS", "ROLE",
                normalizedRole, previous.toString(), requested.toString());
        log.info("Role permissions updated, operatorUserId={}, role={}, permissionCount={}",
                operatorUserId, normalizedRole, requested.size());
        return accessControlMapper.selectRoles().stream()
                .filter(role -> role.code().equals(normalizedRole))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR))
                .withPermissions(requested.stream().sorted().toList());
    }

    @Override
    @Transactional
    public AccessRoleResponse createRole(Long operatorUserId, String code, String name, String description) {
        String normalizedCode = code.trim().toUpperCase();
        if (accessControlMapper.countRole(normalizedCode) > 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "角色编码已存在");
        }
        accessControlMapper.insertRole(normalizedCode, name.trim(), description.trim());
        accessControlMapper.insertAudit(operatorUserId, "CREATE_ROLE", "ROLE",
                normalizedCode, null, name.trim());
        log.info("Role created, operatorUserId={}, role={}", operatorUserId, normalizedCode);
        return new AccessRoleResponse(normalizedCode, name.trim(), description.trim(), false, List.of());
    }

    @Override
    @Transactional
    public AccessRouteResponse saveRoute(Long operatorUserId, SaveRouteRequest request) {
        String routeKey = request.routeKey().trim();
        String routeType = request.routeType().trim().toUpperCase();
        boolean page = "PAGE".equals(routeType);
        validateRoute(request, routeKey, page);

        String permissionCode = page ? "route:" + routeKey : null;
        String path = page ? request.path().trim() : null;
        String componentKey = page ? request.componentKey().trim().toUpperCase() : null;
        String parentKey = normalizeNullable(request.parentKey());
        String icon = normalizeNullable(request.icon());
        if (page) {
            accessControlMapper.upsertPermission(
                    permissionCode,
                    "访问" + request.name().trim(),
                    "允许访问" + request.name().trim() + "页面"
            );
        }

        boolean exists = accessControlMapper.countRoute(routeKey) > 0;
        if (exists) {
            accessControlMapper.updateRoute(routeKey, parentKey, request.name().trim(), path, icon,
                    routeType, componentKey, permissionCode, request.sortOrder(), request.enabled());
        } else {
            accessControlMapper.insertRoute(routeKey, parentKey, request.name().trim(), path, icon,
                    routeType, componentKey, permissionCode, request.sortOrder(), request.enabled());
        }
        accessControlMapper.insertAudit(operatorUserId, exists ? "UPDATE_ROUTE" : "CREATE_ROUTE", "ROUTE",
                routeKey, null, request.name().trim());
        log.info("Route saved, operatorUserId={}, routeKey={}, type={}",
                operatorUserId, routeKey, routeType);
        return accessControlMapper.selectRoutes().stream()
                .filter(route -> route.routeKey().equals(routeKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
    }

    private void validateRoute(SaveRouteRequest request, String routeKey, boolean page) {
        String parentKey = normalizeNullable(request.parentKey());
        if (routeKey.equals(parentKey)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "路由不能将自身设为父级");
        }
        if (parentKey != null && accessControlMapper.countRoute(parentKey) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "父级路由不存在");
        }
        if (page && (request.path() == null || request.componentKey() == null)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "页面路由必须配置路径和组件");
        }
        if (!page && (request.path() != null || request.componentKey() != null)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "分组和菜单不能配置页面路径或组件");
        }
        if (page && accessControlMapper.countOtherRoutePath(routeKey, request.path().trim()) > 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "页面路径已被其他路由使用");
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void assertRoleExists(String roleCode) {
        if (accessControlMapper.countRole(roleCode) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "角色不存在");
        }
    }
}
