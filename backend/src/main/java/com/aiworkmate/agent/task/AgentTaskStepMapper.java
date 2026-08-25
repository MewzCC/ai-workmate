package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AgentTaskStepMapper extends BaseMapper<AgentTaskStep> {

    @Select("""
            SELECT * FROM agent_task_step
            WHERE task_id = #{taskId}
            ORDER BY sequence_no
            """)
    List<AgentTaskStep> selectByTaskId(@Param("taskId") Long taskId);

    @Update("""
            UPDATE agent_task_step SET status='CANCELLED', finished_at=CURRENT_TIMESTAMP,
                version=version+1, updated_at=CURRENT_TIMESTAMP
            WHERE task_id=#{taskId} AND status='PENDING'
            """)
    int cancelPending(@Param("taskId") Long taskId);
}
