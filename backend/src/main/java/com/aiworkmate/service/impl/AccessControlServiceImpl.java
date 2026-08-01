package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.AvatarUrls;
import com.aiworkmate.dto.AccessControlOverviewResponse;
import com.aiworkmate.dto.AccessPermissionResponse;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.dto.AccessUserResponse;
import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.PositionResponse;
import com.aiworkmate.dto.SaveRouteRequest;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessControlServiceImpl implements AccessControlService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AccessControlMapper accessControlMapper;

    @Override
    @Transactional(readOnly = true)
    public AccessControlOverviewResponse overview() {
        return overview(accessControlMapper.selectDefaultTenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public AccessControlOverviewResponse overview(Long tenantId) {
        List<AccessPermissionResponse> permissions = accessControlMapper.selectPermissionsForTenant(tenantId);
        List<String> allPermissionCodes = permissions.stream().map(AccessPermissionResponse::code).toList();
        List<AccessRoleResponse> roles = accessControlMapper.selectRolesForTenant(tenantId).stream()
                .map(role -> role.withPermissions(SUPER_ADMIN.equals(role.code())
                        ? allPermissionCodes
                        : accessControlMapper.selectPermissionCodesForRoles(tenantId, List.of(role.code()))))
                .toList();
        return new AccessControlOverviewResponse(
                accessControlMapper.selectUsers(tenantId).stream()
                        .map(row -> toUser(tenantId, row))
                        .toList(),
                roles,
                List.copyOf(permissions),
                List.copyOf(accessControlMapper.selectRoutesForTenant(tenantId)),
                List.copyOf(accessControlMapper.selectDepartments(tenantId)),
                List.copyOf(accessControlMapper.selectPositions(tenantId))
        );
    }

    @Override
    @Transactional
    public AccessUserResponse assignRole(Long operatorUserId, Long userId, String roleCode) {
        Long tenantId = accessControlMapper.selectUserTenantId(userId);
        if (tenantId != null) {
            return assignRoles(operatorUserId, tenantId, userId, Set.of(roleCode));
        }
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
        return accessControlMapper.selectUsers(tenantId).stream()
                .filter(user -> user.id().equals(userId))
                .map(row -> toUser(tenantId, row))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
    }

    @Override
    @Transactional
    public AccessUserResponse assignRoles(Long operatorUserId,
                                          Long tenantId,
                                          Long userId,
                                          Set<String> roleCodes) {
        Set<String> normalized = roleCodes.stream()
                .map(value -> value.trim().toUpperCase())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "用户至少需要一个角色");
        }
        if (!tenantId.equals(accessControlMapper.selectUserTenantId(userId))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        for (String roleCode : normalized) {
            if (accessControlMapper.countRoleForTenant(tenantId, roleCode) == 0) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID, "角色不存在：" + roleCode);
            }
        }
        List<String> previous = accessControlMapper.selectUserRoleCodes(tenantId, userId);
        if (previous.contains(SUPER_ADMIN)
                && !normalized.contains(SUPER_ADMIN)
                && accessControlMapper.countActiveSuperAdminsForTenant(tenantId) <= 1) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "必须至少保留一名有效超级管理员");
        }
        accessControlMapper.deleteUserRoles(tenantId, userId);
        accessControlMapper.insertUserRoles(tenantId, userId, normalized);
        String primaryRole = normalized.stream()
                .min(Comparator.comparingInt(this::rolePriority))
                .orElseThrow();
        accessControlMapper.updateUserRolesVersion(tenantId, userId, primaryRole);
        accessControlMapper.insertAudit(operatorUserId, "ASSIGN_USER_ROLES", "USER",
                String.valueOf(userId), previous.toString(), normalized.toString());
        return findUser(tenantId, userId);
    }

    @Override
    @Transactional
    public AccessUserResponse updateUserOrganization(Long operatorUserId,
                                                     Long tenantId,
                                                     Long userId,
                                                     Long departmentId,
                                                     Long positionId,
                                                     Long approverUserId) {
        if (!tenantId.equals(accessControlMapper.selectUserTenantId(userId))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (accessControlMapper.countDepartment(tenantId, departmentId) == 0
                || accessControlMapper.countPosition(tenantId, positionId) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "部门或岗位不存在");
        }
        if (approverUserId != null) {
            if (approverUserId.equals(userId)) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID, "审批人不能是用户本人");
            }
            if (accessControlMapper.countActiveUser(tenantId, approverUserId) == 0) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID, "审批人不存在或已停用");
            }
        }
        accessControlMapper.updateUserOrganization(
                tenantId, userId, departmentId, positionId, approverUserId);
        accessControlMapper.insertAudit(operatorUserId, "UPDATE_USER_ORGANIZATION", "USER",
                userId.toString(), null, departmentId + "/" + positionId + "/" + approverUserId);
        return findUser(tenantId, userId);
    }

    @Override
    @Transactional
    public AccessUserResponse updateUserStatus(Long operatorUserId,
                                               Long tenantId,
                                               Long userId,
                                               Integer status) {
        AccessUserResponse current = findUser(tenantId, userId);
        if (status == 0
                && current.roles().contains(SUPER_ADMIN)
                && accessControlMapper.countActiveSuperAdminsForTenant(tenantId) <= 1) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "最后一名有效超级管理员不能停用");
        }
        accessControlMapper.updateUserStatus(tenantId, userId, status);
        accessControlMapper.insertAudit(operatorUserId, "UPDATE_USER_STATUS", "USER",
                userId.toString(), current.status().toString(), status.toString());
        return findUser(tenantId, userId);
    }

    @Override
    @Transactional
    public DepartmentResponse saveDepartment(Long operatorUserId,
                                             Long tenantId,
                                             String code,
                                             String name,
                                             Long parentId,
                                             Long defaultApproverUserId) {
        if (parentId != null && accessControlMapper.countDepartment(tenantId, parentId) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "上级部门不存在");
        }
        if (defaultApproverUserId != null
                && accessControlMapper.countActiveUser(tenantId, defaultApproverUserId) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "默认审批人不存在或已停用");
        }
        String normalizedCode = code.trim().toUpperCase();
        accessControlMapper.saveDepartment(
                tenantId, normalizedCode, name.trim(), parentId, defaultApproverUserId);
        accessControlMapper.insertAudit(operatorUserId, "SAVE_DEPARTMENT", "DEPARTMENT",
                normalizedCode, null, name.trim());
        return accessControlMapper.selectDepartments(tenantId).stream()
                .filter(item -> item.code().equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
    }

    @Override
    @Transactional
    public PositionResponse savePosition(Long operatorUserId,
                                         Long tenantId,
                                         String code,
                                         String name) {
        String normalizedCode = code.trim().toUpperCase();
        accessControlMapper.savePosition(tenantId, normalizedCode, name.trim());
        accessControlMapper.insertAudit(operatorUserId, "SAVE_POSITION", "POSITION",
                normalizedCode, null, name.trim());
        return accessControlMapper.selectPositions(tenantId).stream()
                .filter(item -> item.code().equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR));
    }

    @Override
    @Transactional
    public void deleteDepartment(Long operatorUserId, Long tenantId, Long departmentId) {
        if (accessControlMapper.countDepartment(tenantId, departmentId) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "部门不存在");
        }
        if (accessControlMapper.countChildDepartments(tenantId, departmentId) > 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "该部门下存在子部门，请先调整子部门");
        }
        if (accessControlMapper.countUsersInDepartment(tenantId, departmentId) > 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "该部门下仍有用户，请先调整用户部门");
        }
        DepartmentResponse current = accessControlMapper.selectDepartments(tenantId).stream()
                .filter(item -> item.id().equals(departmentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_INVALID, "部门不存在"));
        int affected = accessControlMapper.deleteDepartment(tenantId, departmentId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除部门失败");
        }
        accessControlMapper.insertAudit(operatorUserId, "DELETE_DEPARTMENT", "DEPARTMENT",
                current.code(), current.name(), null);
        log.info("Department deleted, operatorUserId={}, departmentId={}, code={}",
                operatorUserId, departmentId, current.code());
    }

    @Override
    @Transactional
    public void deletePosition(Long operatorUserId, Long tenantId, Long positionId) {
        if (accessControlMapper.countPosition(tenantId, positionId) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "岗位不存在");
        }
        if (accessControlMapper.countUsersInPosition(tenantId, positionId) > 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "该岗位下仍有用户，请先调整用户岗位");
        }
        PositionResponse current = accessControlMapper.selectPositions(tenantId).stream()
                .filter(item -> item.id().equals(positionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_INVALID, "岗位不存在"));
        int affected = accessControlMapper.deletePosition(tenantId, positionId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除岗位失败");
        }
        accessControlMapper.insertAudit(operatorUserId, "DELETE_POSITION", "POSITION",
                current.code(), current.name(), null);
        log.info("Position deleted, operatorUserId={}, positionId={}, code={}",
                operatorUserId, positionId, current.code());
    }

    @Override
    @Transactional
    public AccessRoleResponse updateRolePermissions(Long operatorUserId,
                                                    String roleCode,
                                                    Set<String> permissionCodes) {
        String normalizedRole = roleCode.trim().toUpperCase();
        Long tenantId = accessControlMapper.selectUserTenantId(operatorUserId);
        if (tenantId == null || accessControlMapper.countRoleForTenant(tenantId, normalizedRole) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "角色不存在");
        }
        if (SUPER_ADMIN.equals(normalizedRole)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "超级管理员始终拥有全部权限");
        }

        Set<String> requested = new TreeSet<>(permissionCodes);
        Set<String> available = Set.copyOf(accessControlMapper.selectAllPermissionCodesForTenant(tenantId));
        if (!available.containsAll(requested)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "包含不存在的权限编码");
        }

        List<String> previous = accessControlMapper.selectPermissionCodesForRoles(
                tenantId, List.of(normalizedRole));
        accessControlMapper.deleteRolePermissionsForTenant(tenantId, normalizedRole);
        if (!requested.isEmpty()) {
            accessControlMapper.insertRolePermissionsForTenant(tenantId, normalizedRole, requested);
        }
        accessControlMapper.insertAudit(operatorUserId, "UPDATE_ROLE_PERMISSIONS", "ROLE",
                normalizedRole, previous.toString(), requested.toString());
        accessControlMapper.incrementPermissionVersionForRole(tenantId, normalizedRole);
        log.info("Role permissions updated, operatorUserId={}, role={}, permissionCount={}",
                operatorUserId, normalizedRole, requested.size());
        return accessControlMapper.selectRolesForTenant(tenantId).stream()
                .filter(role -> role.code().equals(normalizedRole))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR))
                .withPermissions(requested.stream().sorted().toList());
    }

    @Override
    @Transactional
    public AccessControlOverviewResponse updateRoleMembers(Long operatorUserId,
                                                           Long tenantId,
                                                           String roleCode,
                                                           Set<Long> userIds) {
        if (!tenantId.equals(accessControlMapper.selectUserTenantId(operatorUserId))) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        String normalizedRole = roleCode.trim().toUpperCase();
        if (accessControlMapper.countRoleForTenant(tenantId, normalizedRole) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Set<Long> requested = new TreeSet<>(userIds);
        Set<Long> current = new TreeSet<>(
                accessControlMapper.selectRoleMemberUserIds(tenantId, normalizedRole));
        Map<Long, AccessUserResponse> usersById = new HashMap<>();
        for (AccessUserRow row : accessControlMapper.selectUsers(tenantId)) {
            usersById.put(row.id(), toUser(tenantId, row));
        }
        if (!usersById.keySet().containsAll(requested)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Set<Long> added = new TreeSet<>(requested);
        added.removeAll(current);
        for (Long userId : added) {
            if (usersById.get(userId).status() != 1) {
                throw new BusinessException(
                        ErrorCode.REQUEST_INVALID, "停用用户不能新增到角色");
            }
        }

        Set<Long> removed = new TreeSet<>(current);
        removed.removeAll(requested);
        for (Long userId : removed) {
            AccessUserResponse user = usersById.get(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (user.roles().size() <= 1) {
                throw new BusinessException(
                        ErrorCode.REQUEST_INVALID, "不能移除用户的唯一角色");
            }
        }

        if (SUPER_ADMIN.equals(normalizedRole)) {
            long remainingActiveSuperAdmins = requested.stream()
                    .map(usersById::get)
                    .filter(user -> user != null && user.status() == 1)
                    .count();
            if (remainingActiveSuperAdmins == 0) {
                throw new BusinessException(
                        ErrorCode.REQUEST_INVALID, "必须至少保留一名有效超级管理员");
            }
        }

        Set<Long> changed = new TreeSet<>(added);
        changed.addAll(removed);
        for (Long userId : changed) {
            Set<String> updatedRoles = new TreeSet<>(usersById.get(userId).roles());
            if (added.contains(userId)) {
                updatedRoles.add(normalizedRole);
            } else {
                updatedRoles.remove(normalizedRole);
            }
            accessControlMapper.deleteUserRoles(tenantId, userId);
            accessControlMapper.insertUserRoles(tenantId, userId, updatedRoles);
            String primaryRole = updatedRoles.stream()
                    .min(Comparator.comparingInt(this::rolePriority))
                    .orElseThrow();
            accessControlMapper.updateUserRolesVersion(tenantId, userId, primaryRole);
        }
        accessControlMapper.insertAudit(
                operatorUserId, "UPDATE_ROLE_MEMBERS", "ROLE", normalizedRole,
                current.toString(), requested.toString());
        log.info("Role members updated, operatorUserId={}, role={}, addedCount={}, removedCount={}",
                operatorUserId, normalizedRole, added.size(), removed.size());
        return overview(tenantId);
    }

    @Override
    @Transactional
    public AccessRoleResponse createRole(Long operatorUserId, String code, String name, String description) {
        String normalizedCode = code.trim().toUpperCase();
        Long tenantId = accessControlMapper.selectUserTenantId(operatorUserId);
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        if (accessControlMapper.countRoleForTenant(tenantId, normalizedCode) > 0) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "角色编码已存在");
        }
        accessControlMapper.insertRoleForTenant(
                tenantId, normalizedCode, name.trim(), description.trim());
        accessControlMapper.insertAudit(operatorUserId, "CREATE_ROLE", "ROLE",
                normalizedCode, null, name.trim());
        log.info("Role created, operatorUserId={}, role={}", operatorUserId, normalizedCode);
        return new AccessRoleResponse(normalizedCode, name.trim(), description.trim(), false, List.of());
    }

    @Override
    @Transactional
    public AccessRouteResponse saveRoute(Long operatorUserId, SaveRouteRequest request) {
        Long tenantId = accessControlMapper.selectUserTenantId(operatorUserId);
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
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
            accessControlMapper.upsertPermissionForTenant(
                    tenantId,
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
            accessControlMapper.insertRouteForTenant(tenantId, routeKey, parentKey,
                    request.name().trim(), path, icon,
                    routeType, componentKey, permissionCode, request.sortOrder(), request.enabled());
        }
        accessControlMapper.insertAudit(operatorUserId, exists ? "UPDATE_ROUTE" : "CREATE_ROUTE", "ROUTE",
                routeKey, null, request.name().trim());
        log.info("Route saved, operatorUserId={}, routeKey={}, type={}",
                operatorUserId, routeKey, routeType);
        return accessControlMapper.selectRoutesForTenant(tenantId).stream()
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

    private AccessUserResponse findUser(Long tenantId, Long userId) {
        return accessControlMapper.selectUsers(tenantId).stream()
                .filter(row -> row.id().equals(userId))
                .map(row -> toUser(tenantId, row))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AccessUserResponse toUser(Long tenantId, AccessUserRow row) {
        List<String> roles = accessControlMapper.selectUserRoleCodes(tenantId, row.id());
        return new AccessUserResponse(
                row.id(), row.name(), row.email(), row.role(), List.copyOf(roles),
                row.status(), row.departmentId(), row.positionId(), row.approverUserId(),
                row.permissionVersion(), row.updatedAt(),
                AvatarUrls.build(row.id(), row.avatar(), row.updatedAt()));
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
