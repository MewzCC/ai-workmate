package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.PageActionOverviewResponse;
import com.aiworkmate.dto.UpdatePageActionPolicyRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.PageActionPolicyService;
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
@RequestMapping("/api/admin/page-actions")
@PreAuthorize("hasAuthority('route:page-actions')")
@RequiredArgsConstructor
public class PageActionPolicyController {
    private final PageActionPolicyService service;

    @GetMapping
    public Result<PageActionOverviewResponse> overview(@AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(service.overview(operator.userId()));
    }

    @PutMapping("/{pageId}/{toolCode}")
    public Result<PageActionOverviewResponse> update(@AuthenticationPrincipal AuthenticatedUser operator,
                                                      @PathVariable String pageId,
                                                      @PathVariable String toolCode,
                                                      @Valid @RequestBody UpdatePageActionPolicyRequest request) {
        return Result.ok(service.update(operator.userId(), pageId, toolCode, request));
    }
}
