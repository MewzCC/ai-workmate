package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.OcrSettingsRequest;
import com.aiworkmate.dto.OcrSettingsResponse;
import com.aiworkmate.dto.ChatPreferencesRequest;
import com.aiworkmate.dto.ChatPreferencesResponse;
import com.aiworkmate.dto.DashboardPreferenceRequest;
import com.aiworkmate.dto.DashboardPreferenceResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.UserSettingsService;
import com.aiworkmate.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;
    private final DashboardService dashboardService;

    @GetMapping("/ocr")
    public Result<OcrSettingsResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(new OcrSettingsResponse(userSettingsService.isForcePdfOcr(user.userId())));
    }

    @PutMapping("/ocr")
    public Result<OcrSettingsResponse> update(@Valid @RequestBody OcrSettingsRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        boolean force = Boolean.TRUE.equals(request.forcePdfOcr());
        userSettingsService.setForcePdfOcr(user.userId(), force);
        return Result.ok(new OcrSettingsResponse(force));
    }

    @GetMapping("/chat")
    public Result<ChatPreferencesResponse> getChatPreferences(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(userSettingsService.getChatPreferences(user.userId()));
    }

    @PutMapping("/chat")
    public Result<ChatPreferencesResponse> updateChatPreferences(
            @Valid @RequestBody ChatPreferencesRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(userSettingsService.updateChatPreferences(user.userId(), request));
    }

    @GetMapping("/dashboard")
    public Result<DashboardPreferenceResponse> getDashboardPreferences(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(dashboardService.preferences(user.userId()));
    }

    @PutMapping("/dashboard")
    public Result<DashboardPreferenceResponse> updateDashboardPreferences(
            @Valid @RequestBody DashboardPreferenceRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(dashboardService.updatePreferences(user.userId(), request));
    }
}
