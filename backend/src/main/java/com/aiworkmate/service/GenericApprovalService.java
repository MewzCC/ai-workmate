package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.ApprovalDraftUpdateRequest;
import com.aiworkmate.dto.ApprovalSubmitRequest;
import com.aiworkmate.dto.VersionRequest;

/**
 * 通用审批提交服务。
 *
 * <p>面向「发起审批」模板中心：任意启用表单按 {@code schema_json}
 * 校验后提交，绑定启用流程并生成工作流实例与首个审批待办。
 */
public interface GenericApprovalService {

    /** 保存一份未提交草稿；必填字段可暂缺，不创建工作流或待办。 */
    ApprovalApplicationResponse createDraft(Long userId, ApprovalDraftRequest request);

    /** 更新本人处于 DRAFT 状态的草稿，使用乐观锁防止覆盖。 */
    ApprovalApplicationResponse updateDraft(Long userId, Long id, ApprovalDraftUpdateRequest request);

    /** 提交本人草稿并原子创建工作流实例与首个待办。 */
    ApprovalApplicationResponse submitDraft(Long userId, Long id, VersionRequest request);

    /** 取消本人草稿；取消后只保留审计记录，不允许继续编辑。 */
    ApprovalApplicationResponse cancelDraft(Long userId, Long id, VersionRequest request);

    /** 撤回本人审批中的申请，同时取消当前流程实例和唯一有效待办。 */
    ApprovalApplicationResponse withdraw(Long userId, Long id, VersionRequest request);

    /** 催办当前有效待办，按服务端频率窗口限流并写入审计与消息中心。 */
    ApprovalApplicationResponse remind(Long userId, Long id, VersionRequest request);

    /** 将本人被拒绝或已撤回的申请恢复为草稿，保留原流程历史供重新提交。 */
    ApprovalApplicationResponse reopen(Long userId, Long id, VersionRequest request);

    /** 按表单 Key 提交一份申请，返回创建后的申请单（含首个待办信息）。 */
    ApprovalApplicationResponse submit(Long userId, ApprovalSubmitRequest request);

    /** 当前用户提交过的通用申请分页。 */
    PageResponse<ApprovalApplicationResponse> mine(Long userId, String status, int page, int size);

    /** 申请详情：申请人、当前待办受理人或具备审计权限者可读。 */
    ApprovalApplicationResponse detail(Long userId, Long id);
}
