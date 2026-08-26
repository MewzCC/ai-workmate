package com.aiworkmate.agent.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    @Insert("""
            INSERT INTO agent_task(task_no, tenant_id, user_id, page_id, input, page_context,
                plan_version, max_risk_level, status, attempt_count, tool_call_count, trace_id, version)
            VALUES(#{taskNo}, #{tenantId}, #{userId}, #{pageId}, #{input}, CAST(#{pageContext} AS jsonb),
                #{planVersion}, #{maxRiskLevel}, #{status}, #{attemptCount}, #{toolCallCount}, #{traceId}, #{version})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertReceived(AgentTask task);

    @Select("""
            SELECT * FROM agent_task
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND task_no = #{taskNo}
            """)
    AgentTask selectOwned(@Param("tenantId") Long tenantId,
                          @Param("userId") Long userId,
                          @Param("taskNo") String taskNo);

    @Select("""
            SELECT created_at FROM agent_task
            WHERE id=#{taskId} AND tenant_id=#{tenantId} AND user_id=#{userId} AND status='RUNNING'
            """)
    LocalDateTime selectCreatedAtForWriteEvidence(@Param("taskId") Long taskId,
                                                   @Param("tenantId") Long tenantId,
                                                   @Param("userId") Long userId);

    @Update("""
            UPDATE agent_task
            SET status = #{targetStatus}, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{taskId} AND status = #{expectedStatus} AND version = #{expectedVersion}
            """)
    int transition(@Param("taskId") Long taskId,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("targetStatus") String targetStatus,
                   @Param("expectedVersion") Long expectedVersion);

    @Update("""
            UPDATE agent_task SET plan=CAST(#{plan} AS jsonb), plan_hash=#{planHash},
                max_risk_level=#{riskLevel}, status=#{status}, planner_model=#{plannerModel},
                prompt_version=#{promptVersion}, planning_latency_ms=#{latencyMs},
                tool_call_count=#{toolCalls}, version=version+1, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{taskId} AND status='PLANNING' AND version=#{version}
            """)
    int finalizePlan(@Param("taskId") long taskId, @Param("version") long version,
                     @Param("plan") String plan, @Param("planHash") String planHash,
                     @Param("riskLevel") String riskLevel, @Param("status") String status,
                     @Param("plannerModel") String plannerModel, @Param("promptVersion") String promptVersion,
                     @Param("latencyMs") long latencyMs, @Param("toolCalls") int toolCalls);

    @Update("""
            UPDATE agent_task SET status='QUEUED', timeout_at=#{timeoutAt}, version=version+1,
                updated_at=CURRENT_TIMESTAMP
            WHERE id=#{taskId} AND tenant_id=#{tenantId} AND user_id=#{userId}
              AND status='PLAN_READY' AND plan_version=#{planVersion} AND plan_hash=#{planHash}
            """)
    int queuePlanReady(@Param("taskId") long taskId, @Param("tenantId") long tenantId,
                       @Param("userId") long userId, @Param("planVersion") int planVersion,
                       @Param("planHash") String planHash, @Param("timeoutAt") LocalDateTime timeoutAt);

    @Select("""
            <script>
            SELECT * FROM agent_task
            WHERE tenant_id=#{tenantId} AND user_id=#{userId}
            <if test='status != null'>AND status=#{status}</if>
            <if test='from != null'>AND created_at &gt;= #{from}</if>
            <if test='to != null'>AND created_at &lt;= #{to}</if>
            ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AgentTask> selectOwnedPage(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                    @Param("status") String status, @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to, @Param("limit") int limit,
                                    @Param("offset") int offset);

    @Select("""
            <script>
            SELECT count(*) FROM agent_task
            WHERE tenant_id=#{tenantId} AND user_id=#{userId}
            <if test='status != null'>AND status=#{status}</if>
            <if test='from != null'>AND created_at &gt;= #{from}</if>
            <if test='to != null'>AND created_at &lt;= #{to}</if>
            </script>
            """)
    long countOwned(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                    @Param("status") String status, @Param("from") LocalDateTime from,
                    @Param("to") LocalDateTime to);

    @Update("""
            UPDATE agent_task SET confirmation_token_hash=#{tokenHash}, confirmation_expires_at=#{expiresAt},
                confirmed_at=CURRENT_TIMESTAMP, confirmation_consumed_at=NULL,
                version=version+1, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{taskId} AND tenant_id=#{tenantId} AND user_id=#{userId}
              AND status='WAITING_CONFIRMATION' AND version=#{version}
              AND plan_version=#{planVersion} AND plan_hash=#{planHash}
            """)
    int issueConfirmation(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId,
                          @Param("userId") Long userId, @Param("version") Long version,
                          @Param("planVersion") Integer planVersion, @Param("planHash") String planHash,
                          @Param("tokenHash") String tokenHash, @Param("expiresAt") LocalDateTime expiresAt);

    @Update("""
            UPDATE agent_task SET status='QUEUED', confirmation_consumed_at=CURRENT_TIMESTAMP,
                confirmation_token_hash=NULL, confirmation_expires_at=NULL,
                timeout_at=#{timeoutAt}, version=version+1, updated_at=CURRENT_TIMESTAMP
            WHERE task_no=#{taskNo} AND tenant_id=#{tenantId} AND user_id=#{userId}
              AND status='WAITING_CONFIRMATION' AND plan_version=#{planVersion} AND plan_hash=#{planHash}
              AND confirmation_token_hash=#{tokenHash} AND confirmation_expires_at>CURRENT_TIMESTAMP
              AND confirmation_consumed_at IS NULL
            """)
    int consumeConfirmation(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                            @Param("taskNo") String taskNo, @Param("planVersion") Integer planVersion,
                            @Param("planHash") String planHash, @Param("tokenHash") String tokenHash,
                            @Param("timeoutAt") LocalDateTime timeoutAt);

    @Update("""
            UPDATE agent_task SET status='CANCELLED', confirmation_token_hash=NULL,
                confirmation_expires_at=NULL, finished_at=CURRENT_TIMESTAMP,
                version=version+1, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{taskId} AND tenant_id=#{tenantId} AND user_id=#{userId}
              AND status=#{status} AND version=#{version}
            """)
    int cancelOwned(@Param("taskId") Long taskId, @Param("tenantId") Long tenantId,
                    @Param("userId") Long userId, @Param("status") String status,
                    @Param("version") Long version);

    @Select("""
            SELECT * FROM agent_task
            WHERE status='WAITING_CONFIRMATION' AND confirmation_expires_at<=CURRENT_TIMESTAMP
            ORDER BY confirmation_expires_at, id FOR UPDATE SKIP LOCKED LIMIT #{limit}
            """)
    List<AgentTask> selectExpiredConfirmations(@Param("limit") int limit);

    @Update("""
            UPDATE agent_task SET status='EXPIRED', confirmation_token_hash=NULL,
                confirmation_expires_at=NULL, finished_at=CURRENT_TIMESTAMP,
                version=version+1, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{taskId} AND status='WAITING_CONFIRMATION' AND version=#{version}
              AND confirmation_expires_at<=CURRENT_TIMESTAMP
            """)
    int expireConfirmation(@Param("taskId") Long taskId, @Param("version") Long version);
}
