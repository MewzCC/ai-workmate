package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    @Select("""
            SELECT * FROM agent_task
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND task_no = #{taskNo}
            """)
    AgentTask selectOwned(@Param("tenantId") Long tenantId,
                          @Param("userId") Long userId,
                          @Param("taskNo") String taskNo);

    @Update("""
            UPDATE agent_task
            SET status = #{targetStatus}, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{taskId} AND status = #{expectedStatus} AND version = #{expectedVersion}
            """)
    int transition(@Param("taskId") Long taskId,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("targetStatus") String targetStatus,
                   @Param("expectedVersion") Long expectedVersion);
}
