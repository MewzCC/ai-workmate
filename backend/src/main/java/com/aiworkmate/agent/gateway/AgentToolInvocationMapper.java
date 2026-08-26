package com.aiworkmate.agent.gateway;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentToolInvocationMapper extends BaseMapper<AgentToolInvocation> {

    @Select("""
            SELECT invocation.decision_id AS decisionId, invocation.tool_code AS toolCode,
                   invocation.tool_version AS toolVersion, invocation.decision,
                   invocation.decision_code AS decisionCode,
                   invocation.handler_invoked AS handlerInvoked, invocation.outcome,
                   invocation.result_bytes AS resultBytes, invocation.error_class AS errorClass,
                   invocation.started_at AS startedAt, invocation.completed_at AS completedAt,
                   invocation.duration_ms AS durationMs
            FROM agent_tool_invocation invocation
            JOIN agent_task task ON task.id=invocation.task_id
            WHERE task.tenant_id=#{tenantId} AND task.user_id=#{userId} AND task.task_no=#{taskNo}
              AND invocation.tenant_id=task.tenant_id AND invocation.user_id=task.user_id
            ORDER BY invocation.started_at DESC, invocation.id DESC LIMIT #{limit}
            """)
    List<AgentInvocationAuditRecord> selectOwnedTaskAudit(@Param("tenantId") long tenantId,
                                                           @Param("userId") long userId,
                                                           @Param("taskNo") String taskNo,
                                                           @Param("limit") int limit);
}
