package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.GenericApprovalTaskDetail;
import com.aiworkmate.dto.GenericApprovalTaskItem;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.GenericApprovalTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/generic-approval-tasks")
@RequiredArgsConstructor
public class GenericApprovalTaskController {
    private final GenericApprovalTaskService service;
    @GetMapping
    public Result<PageResponse<GenericApprovalTaskItem>> mine(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required=false) String status, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return Result.ok(service.mine(user.userId(), null, status, from, to, page, size));
    }
    @GetMapping("/{id}")
    public Result<GenericApprovalTaskDetail> detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @RequestParam(defaultValue="1") int fieldPage) { return Result.ok(service.detail(user.userId(), null, id, fieldPage)); }
    @PostMapping("/{id}/approve")
    public Result<GenericApprovalTaskDetail> approve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request) { return Result.ok(service.approve(user.userId(), null, id, request)); }
    @PostMapping("/{id}/reject")
    public Result<GenericApprovalTaskDetail> reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request) { return Result.ok(service.reject(user.userId(), null, id, request)); }
}
