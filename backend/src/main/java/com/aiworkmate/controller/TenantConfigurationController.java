package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.TenantBusinessRequest;
import com.aiworkmate.dto.TenantConfigurationHistoryPageResponse;
import com.aiworkmate.dto.TenantConfigurationResponse;
import com.aiworkmate.dto.TenantFeaturesRequest;
import com.aiworkmate.dto.TenantProfileRequest;
import com.aiworkmate.dto.TenantSecurityRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.TenantConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tenant-config")
public class TenantConfigurationController {
    private final TenantConfigurationService service;

    @GetMapping
    public Result<TenantConfigurationResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(service.get(user.userId()));
    }

    @PutMapping("/profile")
    public Result<TenantConfigurationResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TenantProfileRequest request) {
        return Result.ok(service.updateProfile(user.userId(), request));
    }

    @PutMapping("/features")
    public Result<TenantConfigurationResponse> updateFeatures(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TenantFeaturesRequest request) {
        return Result.ok(service.updateFeatures(user.userId(), request));
    }

    @PutMapping("/business")
    public Result<TenantConfigurationResponse> updateBusiness(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TenantBusinessRequest request) {
        return Result.ok(service.updateBusiness(user.userId(), request));
    }

    @PutMapping("/security")
    public Result<TenantConfigurationResponse> updateSecurity(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TenantSecurityRequest request) {
        return Result.ok(service.updateSecurity(user.userId(), request));
    }

    @GetMapping("/history")
    public Result<TenantConfigurationHistoryPageResponse> history(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.history(user.userId(), page, size));
    }
}
