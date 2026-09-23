package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.PlatformObservabilityResponse;
import com.aiworkmate.dto.PlatformObservabilityTimelineResponse;
import com.aiworkmate.dto.PlatformObservabilityComparisonResponse;
import com.aiworkmate.dto.ObservabilityPreferenceRequest;
import com.aiworkmate.dto.ObservabilityPreferenceResponse;
import com.aiworkmate.dto.ObservabilityThresholdPreference;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.PlatformObservabilityService;
import com.aiworkmate.service.ObservabilityPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/platform-observability")
@PreAuthorize("hasAuthority('route:platform-observability') and hasAuthority('runtime-log:read')")
@RequiredArgsConstructor
public class PlatformObservabilityController {
    private final PlatformObservabilityService service;
    private final ObservabilityPreferenceService preferenceService;

    @GetMapping("/overview")
    public Result<PlatformObservabilityResponse> overview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "7d") String range) {
        return Result.ok(service.overview(user.userId(), range));
    }

    @GetMapping("/timeline")
    public Result<PlatformObservabilityTimelineResponse> timeline(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam String interval) {
        return Result.ok(service.timeline(user.userId(), from, to, interval));
    }

    @GetMapping("/comparison")
    public Result<PlatformObservabilityComparisonResponse> comparison(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "7d") String range) {
        return Result.ok(service.comparison(user.userId(), range));
    }

    @GetMapping("/preferences")
    public Result<ObservabilityPreferenceResponse> preferences(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(preferenceService.preferences(user.userId()));
    }

    @PutMapping("/preferences")
    public Result<ObservabilityPreferenceResponse> updatePreferences(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ObservabilityPreferenceRequest request) {
        return Result.ok(preferenceService.update(user.userId(), request));
    }

    @GetMapping("/thresholds")
    public Result<ObservabilityThresholdPreference> thresholds(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(preferenceService.thresholds(user.userId()));
    }

    @PutMapping("/thresholds")
    public Result<ObservabilityThresholdPreference> updateThresholds(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ObservabilityThresholdPreference request) {
        return Result.ok(preferenceService.updateThresholds(user.userId(), request));
    }
}
