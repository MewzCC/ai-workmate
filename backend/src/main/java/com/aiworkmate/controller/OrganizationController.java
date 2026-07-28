package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.OrganizationMemberResponse;
import com.aiworkmate.dto.OrganizationOverviewResponse;
import com.aiworkmate.dto.PositionResponse;
import com.aiworkmate.dto.SaveDepartmentRequest;
import com.aiworkmate.dto.SavePositionRequest;
import com.aiworkmate.dto.UpdateUserOrganizationRequest;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AccessControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization")
@RequiredArgsConstructor
public class OrganizationController {

    private final AccessControlMapper mapper;
    private final AccessControlService service;

    @GetMapping
    @PreAuthorize("hasAuthority('org:read')")
    public Result<OrganizationOverviewResponse> overview(
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(new OrganizationOverviewResponse(
                mapper.selectDepartments(operator.tenantId()),
                mapper.selectPositions(operator.tenantId()),
                mapper.selectUsers(operator.tenantId()).stream()
                        .filter(user -> user.status() == 1)
                        .map(user -> new OrganizationMemberResponse(
                                user.id(), user.name(), user.departmentId(),
                                user.positionId(), user.approverUserId()))
                        .toList(),
                operator.permissions().contains("org:manage")
                        || operator.roles().contains("SUPER_ADMIN")
        ));
    }

    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('org:manage')")
    public Result<DepartmentResponse> saveDepartment(
            @Valid @RequestBody SaveDepartmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(service.saveDepartment(
                operator.userId(), operator.tenantId(), request.code(), request.name(),
                request.parentId(), request.defaultApproverUserId()));
    }

    @PostMapping("/positions")
    @PreAuthorize("hasAuthority('org:manage')")
    public Result<PositionResponse> savePosition(
            @Valid @RequestBody SavePositionRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(service.savePosition(
                operator.userId(), operator.tenantId(), request.code(), request.name()));
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('org:manage')")
    public Result<?> updateMember(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserOrganizationRequest request,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(service.updateUserOrganization(
                operator.userId(), operator.tenantId(), userId,
                request.departmentId(), request.positionId(), request.approverUserId()));
    }

    @DeleteMapping("/departments/{departmentId}")
    @PreAuthorize("hasAuthority('org:manage')")
    public Result<Void> deleteDepartment(
            @PathVariable Long departmentId,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        service.deleteDepartment(operator.userId(), operator.tenantId(), departmentId);
        return Result.ok();
    }

    @DeleteMapping("/positions/{positionId}")
    @PreAuthorize("hasAuthority('org:manage')")
    public Result<Void> deletePosition(
            @PathVariable Long positionId,
            @AuthenticationPrincipal AuthenticatedUser operator) {
        service.deletePosition(operator.userId(), operator.tenantId(), positionId);
        return Result.ok();
    }
}
