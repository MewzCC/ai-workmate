package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_task_event")
public class AgentTaskEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String eventType;
    private String payload;
    private String traceId;
    private LocalDateTime createdAt;
}
