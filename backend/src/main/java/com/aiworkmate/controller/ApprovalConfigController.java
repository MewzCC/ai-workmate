package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ApprovalFormRequest;
import com.aiworkmate.dto.ApprovalFormResponse;
import com.aiworkmate.dto.ApprovalProcessRequest;
import com.aiworkmate.dto.ApprovalProcessResponse;
import com.aiworkmate.dto.ApprovalRuleRequest;
import com.aiworkmate.dto.ApprovalRuleResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.ApprovalEngineService;
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

/**
 * 审批引擎配置 REST 接口。
 *
 * <p>三个子模块共享业务前缀：
 * <ul>
 *   <li>表单引擎 {@code /api/approval-forms} — 审批表单定义 CRUD；</li>
 *   <li>流程配置 {@code /api/approval-processes} — 审批流程定义 CRUD；</li>
 *   <li>审批规则 {@code /api/approval-rules} — 审批规则 CRUD。</li>
 * </ul>
 *
 * <p>所有接口由 {@link com.aiworkmate.config.SecurityConfig} 默认要求 JWT 认证，
 * 业务权限由 Service 层按当前认证 {@code userId} 实时解析（读 {@code approval:read}，
 * 写 {@code approval:manage}）。
 */
@RestController
@RequestMapping("/api/approval-config")
@RequiredArgsConstructor
public class ApprovalConfigController {

    private final ApprovalEngineService service;

    // ==================== 表单引擎 ====================

    @GetMapping("/forms")
    public Result<PageResponse<ApprovalFormResponse>> listForms(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listForms(user.userId(), keyword, status, page, size));
    }

    @GetMapping("/forms/{id}")
    public Result<ApprovalFormResponse> getForm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.getForm(user.userId(), id));
    }

    @PostMapping("/forms")
    public Result<ApprovalFormResponse> createForm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ApprovalFormRequest request) {
        return Result.ok(service.createForm(user.userId(), request));
    }

    @PutMapping("/forms/{id}")
    public Result<ApprovalFormResponse> updateForm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalFormRequest request) {
        return Result.ok(service.updateForm(user.userId(), id, request));
    }

    @DeleteMapping("/forms/{id}")
    public Result<Void> deleteForm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        service.deleteForm(user.userId(), id);
        return Result.ok();
    }

    // ==================== 流程配置 ====================

    @GetMapping("/processes")
    public Result<PageResponse<ApprovalProcessResponse>> listProcesses(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listProcesses(user.userId(), keyword, status, page, size));
    }

    @GetMapping("/processes/{id}")
    public Result<ApprovalProcessResponse> getProcess(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.getProcess(user.userId(), id));
    }

    @PostMapping("/processes")
    public Result<ApprovalProcessResponse> createProcess(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ApprovalProcessRequest request) {
        return Result.ok(service.createProcess(user.userId(), request));
    }

    @PutMapping("/processes/{id}")
    public Result<ApprovalProcessResponse> updateProcess(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalProcessRequest request) {
        return Result.ok(service.updateProcess(user.userId(), id, request));
    }

    @DeleteMapping("/processes/{id}")
    public Result<Void> deleteProcess(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        service.deleteProcess(user.userId(), id);
        return Result.ok();
    }

    // ==================== 审批规则 ====================

    @GetMapping("/rules")
    public Result<PageResponse<ApprovalRuleResponse>> listRules(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.listRules(user.userId(), keyword, status, page, size));
    }

    @GetMapping("/rules/{id}")
    public Result<ApprovalRuleResponse> getRule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.getRule(user.userId(), id));
    }

    @PostMapping("/rules")
    public Result<ApprovalRuleResponse> createRule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ApprovalRuleRequest request) {
        return Result.ok(service.createRule(user.userId(), request));
    }

    @PutMapping("/rules/{id}")
    public Result<ApprovalRuleResponse> updateRule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalRuleRequest request) {
        return Result.ok(service.updateRule(user.userId(), id, request));
    }

    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        service.deleteRule(user.userId(), id);
        return Result.ok();
    }
}