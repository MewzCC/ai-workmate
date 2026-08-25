package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AgentTaskStepMapper extends BaseMapper<AgentTaskStep> {

    @Insert("""
            INSERT INTO agent_task_step(task_id, sequence_no, tool_code, tool_version, schema_hash,
                args, args_hash, risk_level, status, attempt_count, trace_id, version)
            VALUES(#{taskId}, #{sequenceNo}, #{toolCode}, #{toolVersion}, #{schemaHash}, CAST(#{args} AS jsonb),
                #{argsHash}, #{riskLevel}, #{status}, #{attemptCount}, #{traceId}, #{version})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPending(AgentTaskStep step);

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
