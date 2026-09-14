package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AiOperationPermissionOverviewResponse;
import com.aiworkmate.dto.UpdateAiRoleToolsRequest;
import com.aiworkmate.dto.UpdateAiTenantPolicyRequest;
import com.aiworkmate.dto.UpdateAiToolStatusRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiOperationPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-operation-permissions")
@PreAuthorize("hasAuthority('agent-permission:manage')")
@RequiredArgsConstructor
public class AiOperationPermissionController {
    private final AiOperationPermissionService service;

    @GetMapping
    public Result<AiOperationPermissionOverviewResponse> overview(
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(service.overview(operator));
    }

    @PutMapping("/tenant-policy")
    public Result<AiOperationPermissionOverviewResponse> updateTenantPolicy(
            @Valid @RequestBody UpdateAiTenantPolicyRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(service.updateTenantPolicy(
                operator, request.enabled(), request.writeToolsEnabled()));
    }

    @PutMapping("/tools/{toolCode}")
    public Result<AiOperationPermissionOverviewResponse> updateToolStatus(
            @PathVariable String toolCode,
            @Valid @RequestBody UpdateAiToolStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(service.updateToolStatus(operator, toolCode, request.enabled()));
    }

    @PutMapping("/roles/{roleCode}")
    public Result<AiOperationPermissionOverviewResponse> updateRoleTools(
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateAiRoleToolsRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(service.updateRoleTools(operator, roleCode, request.toolCodes()));
    }
}
