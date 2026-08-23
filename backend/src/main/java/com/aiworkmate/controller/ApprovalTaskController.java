package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.ApprovalStatusCountResponse;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.WorkflowTimelineResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.LeaveWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/approval-tasks")
@RequiredArgsConstructor
public class ApprovalTaskController {

    private final LeaveWorkflowService service;

    @GetMapping
    public Result<PageResponse<LeaveApplicationResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String leaveType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.adminList(user.userId(), status, from, to, keyword, leaveType, page, size));
    }

    @GetMapping("/stats")
    public Result<List<ApprovalStatusCountResponse>> stats(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(service.adminStats(user.userId()));
    }

    @PostMapping("/{id}/approve")
    public Result<LeaveApplicationResponse> approve(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        return Result.ok(service.approve(user.userId(), id, request));
    }

    @PostMapping("/{id}/reject")
    public Result<LeaveApplicationResponse> reject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request) {
        return Result.ok(service.reject(user.userId(), id, request));
    }

    @GetMapping("/{id}/timeline")
    public Result<List<WorkflowTimelineResponse>> timeline(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.timeline(user.userId(), id));
    }
}
