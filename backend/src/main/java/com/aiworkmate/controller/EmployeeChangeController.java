package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.EmployeeChangeDecisionRequest;
import com.aiworkmate.dto.EmployeeChangeRequest;
import com.aiworkmate.dto.EmployeeChangeResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.EmployeeChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hr/employee-changes")
@RequiredArgsConstructor
public class EmployeeChangeController {
    private final EmployeeChangeService service;

    @GetMapping
    @PreAuthorize("hasAuthority('hr:read')")
    public Result<PageResponse<EmployeeChangeResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(user.userId(), status, changeType, keyword, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('hr:read')")
    public Result<EmployeeChangeResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return Result.ok(service.detail(user.userId(), id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('hr:manage')")
    public Result<EmployeeChangeResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody EmployeeChangeRequest request) {
        return Result.ok(service.create(user.userId(), request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('hr:manage')")
    public Result<EmployeeChangeResponse> approve(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody EmployeeChangeDecisionRequest request) {
        return Result.ok(service.approve(user.userId(), id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('hr:manage')")
    public Result<EmployeeChangeResponse> reject(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody EmployeeChangeDecisionRequest request) {
        return Result.ok(service.reject(user.userId(), id, request));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('hr:manage')")
    public Result<EmployeeChangeResponse> withdraw(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody VersionRequest request) {
        return Result.ok(service.withdraw(user.userId(), id, request));
    }
}
