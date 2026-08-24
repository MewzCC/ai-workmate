package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalSubmitRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.GenericApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用审批提交接口（发起审批模板中心）。
 *
 * <p>所有端点均需认证；提交与「我的申请」需 {@code route:approval-start}，
 * 详情按申请人/当前受理人/审计权限校验归属。
 */
@RestController
@RequestMapping("/api/approval-applications")
@RequiredArgsConstructor
public class ApprovalApplicationController {

    private final GenericApprovalService service;

    @PostMapping
    public Result<ApprovalApplicationResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ApprovalSubmitRequest request) {
        return Result.ok(service.submit(user.userId(), request));
    }

    @GetMapping("/mine")
    public Result<PageResponse<ApprovalApplicationResponse>> mine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.mine(user.userId(), status, page, size));
    }

    @GetMapping("/{id}")
    public Result<ApprovalApplicationResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.detail(user.userId(), id));
    }
}
