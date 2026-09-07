package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.dto.WorkbenchRecordRequest;
import com.aiworkmate.dto.WorkbenchRecordResponse;
import com.aiworkmate.dto.WorkbenchPageResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.WorkbenchRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workbench/modules/{moduleKey}/records")
@RequiredArgsConstructor
public class WorkbenchRecordController {
    private final WorkbenchRecordService service;

    @GetMapping
    public Result<WorkbenchPageResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String moduleKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(user.userId(), moduleKey, keyword, status, page, size));
    }

    @PostMapping
    public Result<WorkbenchRecordResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String moduleKey,
            @Valid @RequestBody WorkbenchRecordRequest request) {
        return Result.ok(service.create(user.userId(), moduleKey, request));
    }

    @PutMapping("/{id}")
    public Result<WorkbenchRecordResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String moduleKey,
            @PathVariable Long id,
            @Valid @RequestBody WorkbenchRecordRequest request) {
        return Result.ok(service.update(user.userId(), moduleKey, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String moduleKey,
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request) {
        service.delete(user.userId(), moduleKey, id, request.version());
        return Result.ok();
    }
}
