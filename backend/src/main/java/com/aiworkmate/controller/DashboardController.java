package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.DashboardOverviewResponse;
import com.aiworkmate.dto.DashboardExportRequest;
import com.aiworkmate.dto.DashboardExportResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.DashboardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAuthority('dashboard:read')")
@Validated
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<DashboardOverviewResponse> overview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days) {
        return Result.ok(dashboardService.overview(user.userId(), days));
    }

    @PostMapping("/export")
    @PreAuthorize("hasAuthority('data:export')")
    public Result<DashboardExportResponse> export(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody DashboardExportRequest request) {
        return Result.ok(dashboardService.export(user.userId(), request));
    }
}
