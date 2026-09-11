package com.aiworkmate.agent.registry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_page_action_policy")
public class AgentPageActionPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String pageId;
    private String toolCode;
    private Boolean enabled;
    private Integer version;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
