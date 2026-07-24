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
    public Result<AccessControlOverviewResponse> overview() {
        return Result.ok(accessControlService.overview());
    }

    @PutMapping("/users/{userId}/role")
    public Result<AccessUserResponse> assignRole(
            @PathVariable Long userId,
            @Valid @RequestBody AssignUserRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.assignRole(operator.userId(), userId, request.roleCode()));
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public Result<AccessRoleResponse> updateRolePermissions(
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(accessControlService.updateRolePermissions(
                operator.userId(), roleCode, request.permissionCodes()));
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
