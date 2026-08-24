package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalSubmitRequest;

/**
 * 通用审批提交服务。
 *
 * <p>面向「发起审批」模板中心：任意启用表单按 {@code schema_json}
 * 校验后提交，绑定启用流程并生成工作流实例与首个审批待办。
 */
public interface GenericApprovalService {

    /** 按表单 Key 提交一份申请，返回创建后的申请单（含首个待办信息）。 */
    ApprovalApplicationResponse submit(Long userId, ApprovalSubmitRequest request);

    /** 当前用户提交过的通用申请分页。 */
    PageResponse<ApprovalApplicationResponse> mine(Long userId, String status, int page, int size);

    /** 申请详情：申请人、当前待办受理人或具备审计权限者可读。 */
    ApprovalApplicationResponse detail(Long userId, Long id);
}
