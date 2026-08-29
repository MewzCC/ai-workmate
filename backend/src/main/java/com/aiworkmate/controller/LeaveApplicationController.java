package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.ApproverCandidateResponse;
import com.aiworkmate.dto.LeaveApprovalContextResponse;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.LeaveWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leave-applications")
@RequiredArgsConstructor
public class LeaveApplicationController {

    private final LeaveWorkflowService service;

    @GetMapping("/approval-context")
    public Result<LeaveApprovalContextResponse> approvalContext(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(service.approvalContext(user.userId()));
    }

    @GetMapping("/approver-candidates")
    public Result<PageResponse<ApproverCandidateResponse>> approverCandidates(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.approverCandidates(user.userId(), keyword, page, size));
    }

    @PostMapping
    public Result<LeaveApplicationResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody LeaveApplicationRequest request) {
        return Result.ok(service.createDraft(user.userId(), request));
    }

    @PutMapping("/{id}")
    public Result<LeaveApplicationResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody LeaveApplicationRequest request) {
        return Result.ok(service.updateDraft(user.userId(), id, request));
    }

    @GetMapping("/{id}")
    public Result<LeaveApplicationResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.getApplication(user.userId(), id));
    }

    @GetMapping("/mine")
    public Result<PageResponse<LeaveApplicationResponse>> mine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.mine(user.userId(), status, page, size));
    }

    @PostMapping("/{id}/submit")
    public Result<LeaveApplicationResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request) {
        return Result.ok(service.submit(user.userId(), id, request));
    }

    @PostMapping("/{id}/withdraw")
    public Result<LeaveApplicationResponse> withdraw(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request) {
        return Result.ok(service.withdraw(user.userId(), id, request));
    }

    @PostMapping("/{id}/remind")
    public Result<LeaveApplicationResponse> remind(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody VersionRequest request) {
        return Result.ok(service.remind(user.userId(), id, request));
    }
}
