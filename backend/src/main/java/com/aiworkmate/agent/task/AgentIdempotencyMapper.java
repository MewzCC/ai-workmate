package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentIdempotencyMapper extends BaseMapper<AgentIdempotency> {

    @Insert("""
            INSERT INTO agent_idempotency(tenant_id, user_id, operation, idempotency_key, request_hash, task_id)
            VALUES(#{tenantId}, #{userId}, #{operation}, #{idempotencyKey}, #{requestHash}, #{taskId})
            ON CONFLICT (tenant_id, user_id, operation, idempotency_key) DO NOTHING
            """)
    int insertIfAbsent(@Param("tenantId") Long tenantId,
                       @Param("userId") Long userId,
                       @Param("operation") String operation,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("requestHash") String requestHash,
                       @Param("taskId") Long taskId);

    @Select("""
            SELECT * FROM agent_idempotency
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
              AND operation = #{operation} AND idempotency_key = #{idempotencyKey}
            """)
    AgentIdempotency selectDomainKey(@Param("tenantId") Long tenantId,
                                     @Param("userId") Long userId,
                                     @Param("operation") String operation,
                                     @Param("idempotencyKey") String idempotencyKey);
}
