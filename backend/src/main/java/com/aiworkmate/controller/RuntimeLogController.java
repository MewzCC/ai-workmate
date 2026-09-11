package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.RuntimeLogDetailResponse;
import com.aiworkmate.dto.RuntimeLogPageResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.RuntimeLogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/runtime-logs")
@PreAuthorize("hasAuthority('route:runtime-logs') and hasAuthority('runtime-log:read')")
@Validated
@RequiredArgsConstructor
public class RuntimeLogController {
    private final RuntimeLogService service;

    @GetMapping
    public Result<RuntimeLogPageResponse> query(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.query(user.userId(), source, outcome, keyword, from, to, page, size));
    }

    @GetMapping("/{source}/{id}")
    public Result<RuntimeLogDetailResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String source,
            @PathVariable @Min(1) Long id) {
        return Result.ok(service.detail(user.userId(), source, id));
    }
}
