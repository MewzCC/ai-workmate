package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用审批申请单（发起审批模板中心提交）。
 *
 * <p>任意启用表单（{@code approval_form}）按 {@code schema_json} 校验后的
 * 提交落库：{@code dataJson} 保存表单数据 JSON 文本；提交成功后绑定
 * {@code approval_process} 启动工作流实例与首个审批待办。状态流转由
 * 后续通用审批决策链路推进；DRAFT 状态不创建工作流，提交时再原子绑定。
 */
@Data
@TableName("approval_application")
public class ApprovalApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long applicantUserId;
    private Long formId;
    private Long processId;
    private String formKey;
    private String formName;
    private String title;
    private String dataJson;
    private String status;
    private Long workflowInstanceId;
    private Integer version;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
