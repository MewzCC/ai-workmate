package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentTaskStepMapper extends BaseMapper<AgentTaskStep> {

    @Select("""
            SELECT * FROM agent_task_step
            WHERE task_id = #{taskId}
            ORDER BY sequence_no
            """)
    List<AgentTaskStep> selectByTaskId(@Param("taskId") Long taskId);
}
