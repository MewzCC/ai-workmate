package com.aiworkmate.agent.retention;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentRetentionMapper {

    @Select("""
            SELECT DISTINCT task.tenant_id
            FROM agent_task_event event
            JOIN agent_task task ON task.id=event.task_id
            WHERE event.created_at<#{cutoff}
            ORDER BY task.tenant_id LIMIT #{limit}
            """)
    List<Long> selectEventTenants(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Delete("""
            WITH candidates AS (
                SELECT event.id FROM agent_task_event event
                JOIN agent_task task ON task.id=event.task_id
                WHERE task.tenant_id=#{tenantId} AND event.created_at<#{cutoff}
                ORDER BY event.created_at, event.id
                FOR UPDATE OF event SKIP LOCKED LIMIT #{limit}
            )
            DELETE FROM agent_task_event event USING candidates WHERE event.id=candidates.id
            """)
    int deleteEventBatch(@Param("tenantId") long tenantId, @Param("cutoff") LocalDateTime cutoff,
                         @Param("limit") int limit);

    @Select("""
            SELECT DISTINCT tenant_id FROM agent_tool_invocation
            WHERE started_at<#{cutoff} ORDER BY tenant_id LIMIT #{limit}
            """)
    List<Long> selectInvocationTenants(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Delete("""
            WITH candidates AS (
                SELECT id FROM agent_tool_invocation
                WHERE tenant_id=#{tenantId} AND started_at<#{cutoff}
                ORDER BY started_at, id FOR UPDATE SKIP LOCKED LIMIT #{limit}
            )
            DELETE FROM agent_tool_invocation invocation USING candidates
            WHERE invocation.id=candidates.id
            """)
    int deleteInvocationBatch(@Param("tenantId") long tenantId, @Param("cutoff") LocalDateTime cutoff,
                              @Param("limit") int limit);

    @Select("""
            SELECT DISTINCT tenant_id FROM agent_task
            WHERE created_at<#{cutoff} AND status IN (
                'SUCCEEDED','PARTIALLY_SUCCEEDED','FAILED','TIMED_OUT','REJECTED','EXPIRED','CANCELLED'
            )
            ORDER BY tenant_id LIMIT #{limit}
            """)
    List<Long> selectTaskTenants(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Delete("""
            WITH candidates AS (
                SELECT id FROM agent_task
                WHERE tenant_id=#{tenantId} AND created_at<#{cutoff} AND status IN (
                    'SUCCEEDED','PARTIALLY_SUCCEEDED','FAILED','TIMED_OUT','REJECTED','EXPIRED','CANCELLED'
                )
                ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT #{limit}
            )
            DELETE FROM agent_task task USING candidates WHERE task.id=candidates.id
            """)
    int deleteTaskBatch(@Param("tenantId") long tenantId, @Param("cutoff") LocalDateTime cutoff,
                        @Param("limit") int limit);
}
