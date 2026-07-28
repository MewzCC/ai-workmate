package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.OrganizationOverviewResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.HrService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hr")
@PreAuthorize("hasAuthority('hr:read')")
@RequiredArgsConstructor
public class HrController {

    private final HrService hrService;

    @GetMapping("/organization")
    public Result<OrganizationOverviewResponse> overview(
            @AuthenticationPrincipal AuthenticatedUser operator) {
        return Result.ok(hrService.overview(operator.tenantId()));
    }
}
