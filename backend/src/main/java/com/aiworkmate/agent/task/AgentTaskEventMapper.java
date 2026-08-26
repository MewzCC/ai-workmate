package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentTaskEventMapper extends BaseMapper<AgentTaskEvent> {

    @Select("""
            INSERT INTO agent_task_event(task_id, event_type, payload, trace_id)
            VALUES (#{taskId}, #{eventType}, CAST(#{payload} AS jsonb), #{traceId})
            RETURNING *
            """)
    AgentTaskEvent insertEvent(AgentTaskEvent event);

    @Select("""
            SELECT event.*
            FROM agent_task_event event
            JOIN agent_task task ON task.id = event.task_id
            WHERE task.tenant_id = #{tenantId}
              AND task.user_id = #{userId}
              AND task.task_no = #{taskNo}
              AND event.id > #{afterEventId}
            ORDER BY event.id
            LIMIT #{limit}
            """)
    List<AgentTaskEvent> selectOwnedEvents(@Param("tenantId") Long tenantId,
                                           @Param("userId") Long userId,
                                           @Param("taskNo") String taskNo,
                                           @Param("afterEventId") Long afterEventId,
                                           @Param("limit") int limit);
}
