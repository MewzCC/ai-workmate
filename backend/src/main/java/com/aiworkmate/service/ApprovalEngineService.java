package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalFormRequest;
import com.aiworkmate.dto.ApprovalFormResponse;
import com.aiworkmate.dto.ApprovalProcessRequest;
import com.aiworkmate.dto.ApprovalProcessResponse;
import com.aiworkmate.dto.ApprovalRuleRequest;
import com.aiworkmate.dto.ApprovalRuleResponse;

/**
 * 审批引擎配置服务：表单引擎 / 流程配置 / 审批规则。
 *
 * <p>三组配置均为租户级 CRUD（软删除 + 乐观锁 version）：
 * 读取需 {@code approval:read}，写入需 {@code approval:manage}，
 * 权限按当前认证 userId 实时解析。
 */
public interface ApprovalEngineService {

    // ==================== 表单定义 ====================

    PageResponse<ApprovalFormResponse> listForms(Long userId, String keyword, String status,
                                                  int page, int size);

    ApprovalFormResponse getForm(Long userId, Long id);

    ApprovalFormResponse createForm(Long userId, ApprovalFormRequest request);

    ApprovalFormResponse updateForm(Long userId, Long id, ApprovalFormRequest request);

    void deleteForm(Long userId, Long id);

    // ==================== 流程定义 ====================

    PageResponse<ApprovalProcessResponse> listProcesses(Long userId, String keyword, String status,
                                                         int page, int size);

    ApprovalProcessResponse getProcess(Long userId, Long id);

    ApprovalProcessResponse createProcess(Long userId, ApprovalProcessRequest request);

    ApprovalProcessResponse updateProcess(Long userId, Long id, ApprovalProcessRequest request);

    void deleteProcess(Long userId, Long id);

    // ==================== 审批规则 ====================

    PageResponse<ApprovalRuleResponse> listRules(Long userId, String keyword, String status,
                                                  int page, int size);

    ApprovalRuleResponse getRule(Long userId, Long id);

    ApprovalRuleResponse createRule(Long userId, ApprovalRuleRequest request);

    ApprovalRuleResponse updateRule(Long userId, Long id, ApprovalRuleRequest request);

    void deleteRule(Long userId, Long id);
}