package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AccessControlOverviewResponse;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AccessUserResponse;
import com.aiworkmate.dto.AssignUserRoleRequest;
import com.aiworkmate.dto.CreateRoleRequest;
import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.dto.SaveRouteRequest;
import com.aiworkmate.dto.UpdateRolePermissionsRequest;
import com.aiworkmate.dto.UpdateRoleMembersRequest;
import com.aiworkmate.dto.AssignUserRolesRequest;
import com.aiworkmate.dto.UpdateUserOrganizationRequest;
import com.aiworkmate.dto.UpdateUserStatusRequest;
import com.aiworkmate.dto.SaveDepartmentRequest;
import com.aiworkmate.dto.SavePositionRequest;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.PositionResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AccessControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access-control")
@PreAuthorize("hasAuthority('access:manage')")
@RequiredArgsConstructor
public class AccessControlController {

    private final AccessControlService accessControlService;

    @GetMapping
    public Result<AccessControlOverviewResponse> overview(
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.overview(operator.tenantId()));
    }

    @PutMapping("/users/{userId}/role")
    public Result<AccessUserResponse> assignRole(
            @PathVariable Long userId,
            @Valid @RequestBody AssignUserRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.assignRole(operator.userId(), userId, request.roleCode()));
    }

    @PutMapping("/users/{userId}/roles")
    public Result<AccessUserResponse> assignRoles(
            @PathVariable Long userId,
            @Valid @RequestBody AssignUserRolesRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.assignRoles(
                operator.userId(), operator.tenantId(), userId, request.roleCodes()));
    }

    @PutMapping("/users/{userId}/organization")
    public Result<AccessUserResponse> updateOrganization(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserOrganizationRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.updateUserOrganization(
                operator.userId(), operator.tenantId(), userId,
                request.departmentId(), request.positionId(), request.approverUserId()));
    }

    @PutMapping("/users/{userId}/status")
    public Result<AccessUserResponse> updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.updateUserStatus(
                operator.userId(), operator.tenantId(), userId, request.status()));
    }

    @PostMapping("/departments")
    public Result<DepartmentResponse> saveDepartment(
            @Valid @RequestBody SaveDepartmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.saveDepartment(
                operator.userId(), operator.tenantId(), request.code(), request.name(),
                request.parentId(), request.defaultApproverUserId()));
    }

    @PostMapping("/positions")
    public Result<PositionResponse> savePosition(
            @Valid @RequestBody SavePositionRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.savePosition(
                operator.userId(), operator.tenantId(), request.code(), request.name()));
    }

    @DeleteMapping("/departments/{departmentId}")
    public Result<Void> deleteDepartment(
            @PathVariable Long departmentId,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        accessControlService.deleteDepartment(operator.userId(), operator.tenantId(), departmentId);
        return Result.ok();
    }

    @DeleteMapping("/positions/{positionId}")
    public Result<Void> deletePosition(
            @PathVariable Long positionId,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        accessControlService.deletePosition(operator.userId(), operator.tenantId(), positionId);
        return Result.ok();
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public Result<AccessRoleResponse> updateRolePermissions(
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.updateRolePermissions(
                operator.userId(), roleCode, request.permissionCodes()));
    }

    @PutMapping("/roles/{roleCode}/members")
    public Result<AccessControlOverviewResponse> updateRoleMembers(
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRoleMembersRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.updateRoleMembers(
                operator.userId(), operator.tenantId(), roleCode, request.userIds()));
    }

    @PostMapping("/roles")
    public Result<AccessRoleResponse> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.createRole(
                operator.userId(), request.code(), request.name(), request.description()));
    }

    @PutMapping("/routes/{routeKey}")
    public Result<AccessRouteResponse> saveRoute(
            @PathVariable String routeKey,
            @Valid @RequestBody SaveRouteRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        if (!routeKey.equals(request.routeKey())) {
            throw new com.aiworkmate.common.BusinessException(
                    com.aiworkmate.common.ErrorCode.REQUEST_INVALID, "路径与请求中的路由编码不一致");
        }
        return Result.ok(accessControlService.saveRoute(operator.userId(), request));
    }
}
