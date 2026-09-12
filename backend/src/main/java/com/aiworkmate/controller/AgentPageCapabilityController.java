package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.PageCapabilityResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.PageCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/pages")
@RequiredArgsConstructor
public class AgentPageCapabilityController {
    private final PageCapabilityService pageCapabilityService;

    @GetMapping("/{pageId}/capabilities")
    public Result<PageCapabilityResponse> capabilities(
            @PathVariable String pageId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(pageCapabilityService.resolve(user.userId(), pageId));
    }
}
