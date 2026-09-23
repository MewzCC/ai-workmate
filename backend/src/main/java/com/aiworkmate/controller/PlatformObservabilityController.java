package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.PlatformObservabilityResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.PlatformObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/platform-observability")
@PreAuthorize("hasAuthority('route:platform-observability') and hasAuthority('runtime-log:read')")
@RequiredArgsConstructor
public class PlatformObservabilityController {
    private final PlatformObservabilityService service;

    @GetMapping("/overview")
    public Result<PlatformObservabilityResponse> overview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "7d") String range) {
        return Result.ok(service.overview(user.userId(), range));
    }
}
