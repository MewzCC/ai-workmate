package com.aiworkmate.agent.gateway;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GatewayExecutionSnapshotMapper {
    @Select("""
            SELECT task.id AS task_id,
                   step.id AS step_id,
                   step.sequence_no,
                   task.tenant_id,
                   task.user_id,
                   task.status AS task_status,
                   step.status AS step_status,
                   task.worker_id,
                   task.lease_token_hash,
                   task.lease_until,
                   task.timeout_at AS task_timeout_at,
                   step.timeout_at AS step_timeout_at,
                   task.attempt_count AS task_attempt,
                   step.attempt_count AS step_attempt,
                   task.plan::text AS plan,
                   task.plan_hash,
                   task.plan_version,
                   task.max_risk_level AS task_risk_level,
                   task.confirmation_consumed_at,
                   task.tool_call_count,
                   step.tool_code,
                   step.tool_version,
                   step.schema_hash,
                   step.args::text AS arguments,
                   step.args_hash,
                   step.risk_level AS step_risk_level,
                   step.trace_id
            FROM agent_task_step step
            JOIN agent_task task ON task.id = step.task_id
            WHERE step.id = #{stepId}
            """)
    GatewayExecutionSnapshot selectSnapshot(long stepId);

    @Update("""
            UPDATE agent_task task
            SET tool_call_count = tool_call_count + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE task.id = #{taskId}
              AND task.status = 'RUNNING'
              AND task.worker_id = #{workerId}
              AND task.lease_token_hash = #{leaseTokenHash}
              AND task.lease_until > CURRENT_TIMESTAMP
              AND task.attempt_count = #{attempt}
              AND task.tool_call_count < #{maxCalls}
              AND EXISTS (
                  SELECT 1 FROM agent_task_step step
                  WHERE step.id = #{stepId}
                    AND step.task_id = task.id
                    AND step.status = 'RUNNING'
                    AND step.attempt_count = #{attempt}
              )
            """)
    int reserveToolCall(@Param("taskId") long taskId,
                        @Param("stepId") long stepId,
                        @Param("workerId") String workerId,
                        @Param("leaseTokenHash") String leaseTokenHash,
                        @Param("attempt") int attempt,
                        @Param("maxCalls") int maxCalls);
}
