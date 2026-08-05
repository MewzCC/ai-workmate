package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.OcrSettingsRequest;
import com.aiworkmate.dto.OcrSettingsResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/ocr")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping
    public Result<OcrSettingsResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(new OcrSettingsResponse(userSettingsService.isForcePdfOcr(user.userId())));
    }

    @PutMapping
    public Result<OcrSettingsResponse> update(@Valid @RequestBody OcrSettingsRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        boolean force = Boolean.TRUE.equals(request.forcePdfOcr());
        userSettingsService.setForcePdfOcr(user.userId(), force);
        return Result.ok(new OcrSettingsResponse(force));
    }
}
