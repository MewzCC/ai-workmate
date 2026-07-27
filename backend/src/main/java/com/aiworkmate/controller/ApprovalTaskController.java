package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.WorkflowTimelineResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.LeaveWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/approval-tasks")
@RequiredArgsConstructor
public class ApprovalTaskController {

    private final LeaveWorkflowService service;

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
