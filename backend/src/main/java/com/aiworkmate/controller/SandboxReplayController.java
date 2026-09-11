package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.SandboxReplayBaselineResponse;
import com.aiworkmate.dto.SandboxReplayDetailResponse;
import com.aiworkmate.dto.SandboxReplayPageResponse;
import com.aiworkmate.dto.SandboxReplayRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.SandboxReplayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/integration/replays")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('route:sandbox-replay') and hasAuthority('integration:replay:read')")
public class SandboxReplayController {
    private final SandboxReplayService service;

    @GetMapping
    public Result<SandboxReplayPageResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.list(user.userId(), keyword, status, page, size));
    }

    @GetMapping("/baselines")
    public Result<List<SandboxReplayBaselineResponse>> baselines(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return Result.ok(service.baselines(user.userId(), keyword, limit));
    }

    @GetMapping("/{id}")
    public Result<SandboxReplayDetailResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.detail(user.userId(), id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('route:sandbox-replay') and hasAuthority('integration:replay:read') "
            + "and hasAuthority('integration:replay:execute')")
    public Result<SandboxReplayDetailResponse> execute(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SandboxReplayRequest request) {
        return Result.ok(service.execute(user.userId(), request));
    }
}
