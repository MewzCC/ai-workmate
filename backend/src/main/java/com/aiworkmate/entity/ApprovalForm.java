package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批表单定义（表单引擎）。
 *
 * <p>{@code schemaJson} 保存表单结构 JSON 文本（字段列表），合法性由应用层校验；
 * 运行时按结构动态渲染表单在后续阶段补充，当前版本为配置维护。删除采用软删除。
 */
@Data
@TableName("approval_form")
public class ApprovalForm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String formKey;
    private String formName;
    private String description;
    private String schemaJson;
    private String status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}