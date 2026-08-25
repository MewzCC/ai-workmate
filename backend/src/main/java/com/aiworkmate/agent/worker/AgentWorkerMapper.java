package com.aiworkmate.agent.worker;

import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AgentWorkerMapper {

    @Select("""
            WITH candidate AS (
                SELECT task.id FROM agent_task task
                WHERE task.status = 'QUEUED' AND task.timeout_at > CURRENT_TIMESTAMP
                  AND (SELECT count(*) FROM agent_task active
                       WHERE active.tenant_id = task.tenant_id AND active.user_id = task.user_id
                         AND active.status = 'RUNNING' AND active.lease_until > CURRENT_TIMESTAMP) < #{userLimit}
                ORDER BY task.created_at, task.id
                FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE agent_task task SET status='RUNNING', worker_id=#{workerId},
                lease_token_hash=#{leaseHash}, lease_until=#{leaseUntil}, heartbeat_at=CURRENT_TIMESTAMP,
                started_at=COALESCE(started_at, CURRENT_TIMESTAMP), version=version+1, updated_at=CURRENT_TIMESTAMP
            FROM candidate WHERE task.id=candidate.id RETURNING task.*
            """)
    AgentTask claim(@Param("workerId") String workerId, @Param("leaseHash") String leaseHash,
                    @Param("leaseUntil") LocalDateTime leaseUntil, @Param("userLimit") int userLimit);

    @Select("""
            WITH candidate AS (
                SELECT step.id FROM agent_task_step step JOIN agent_task task ON task.id=step.task_id
                WHERE task.id=#{taskId} AND task.status='RUNNING' AND task.worker_id=#{workerId}
                  AND task.lease_token_hash=#{leaseHash} AND task.lease_until>CURRENT_TIMESTAMP
                  AND task.attempt_count=#{attempt} AND step.status='PENDING'
                  AND NOT EXISTS (SELECT 1 FROM agent_task_step prior WHERE prior.task_id=task.id
                                  AND prior.sequence_no<step.sequence_no AND prior.status<>'SUCCEEDED')
                ORDER BY step.sequence_no FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE agent_task_step step SET status='RUNNING', attempt_count=#{attempt},
                timeout_at=LEAST(#{stepTimeout}, (SELECT timeout_at FROM agent_task WHERE id=#{taskId})),
                started_at=COALESCE(started_at,CURRENT_TIMESTAMP), version=version+1, updated_at=CURRENT_TIMESTAMP
            FROM candidate WHERE step.id=candidate.id RETURNING step.*
            """)
    AgentTaskStep startNextStep(@Param("taskId") long taskId, @Param("workerId") String workerId,
                                @Param("leaseHash") String leaseHash, @Param("attempt") int attempt,
                                @Param("stepTimeout") LocalDateTime stepTimeout);

    @Update("""
            UPDATE agent_task_step step SET status='SUCCEEDED', result=CAST(#{result} AS jsonb),
                finished_at=CURRENT_TIMESTAMP, version=version+1, updated_at=CURRENT_TIMESTAMP
            FROM agent_task task WHERE step.id=#{stepId} AND step.task_id=task.id
              AND step.status='RUNNING' AND step.attempt_count=#{attempt}
              AND task.worker_id=#{workerId} AND task.lease_token_hash=#{leaseHash}
            """)
    int completeStep(@Param("stepId") long stepId, @Param("attempt") int attempt,
                     @Param("workerId") String workerId, @Param("leaseHash") String leaseHash,
                     @Param("result") String result);

    @Update("""
            UPDATE agent_task SET status='SUCCEEDED', finished_at=CURRENT_TIMESTAMP, worker_id=NULL,
                lease_token_hash=NULL, lease_until=NULL, heartbeat_at=NULL, version=version+1, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{taskId} AND status='RUNNING' AND worker_id=#{workerId} AND lease_token_hash=#{leaseHash}
              AND NOT EXISTS (SELECT 1 FROM agent_task_step WHERE task_id=#{taskId} AND status<>'SUCCEEDED')
            """)
    int completeTask(@Param("taskId") long taskId, @Param("workerId") String workerId,
                     @Param("leaseHash") String leaseHash);

    @Update("""
            WITH failed_step AS (
                UPDATE agent_task_step SET status='FAILED', error_code=#{errorCode},
                    error_message=#{errorMessage}, finished_at=CURRENT_TIMESTAMP,
                    version=version+1, updated_at=CURRENT_TIMESTAMP
                WHERE id=#{stepId} AND status='RUNNING' AND attempt_count=#{attempt} RETURNING task_id
            )
            UPDATE agent_task task SET status='FAILED', error_code=#{errorCode}, error_message=#{errorMessage},
                finished_at=CURRENT_TIMESTAMP, worker_id=NULL, lease_token_hash=NULL, lease_until=NULL,
                heartbeat_at=NULL, version=version+1, updated_at=CURRENT_TIMESTAMP
            FROM failed_step WHERE task.id=failed_step.task_id AND task.worker_id=#{workerId}
              AND task.lease_token_hash=#{leaseHash}
            """)
    int fail(@Param("stepId") long stepId, @Param("attempt") int attempt,
             @Param("workerId") String workerId, @Param("leaseHash") String leaseHash,
             @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    @Update("""
            WITH reset_step AS (
                UPDATE agent_task_step step SET status='PENDING', attempt_count=step.attempt_count+1,
                    timeout_at=NULL, started_at=NULL, version=step.version+1, updated_at=CURRENT_TIMESTAMP
                WHERE step.id=#{stepId} AND step.status='RUNNING' AND step.risk_level='L0'
                  AND step.attempt_count=#{attempt} AND step.attempt_count<2 RETURNING step.task_id
            )
            UPDATE agent_task task SET status='QUEUED', attempt_count=task.attempt_count+1,
                worker_id=NULL, lease_token_hash=NULL, lease_until=NULL, heartbeat_at=NULL,
                version=task.version+1, updated_at=CURRENT_TIMESTAMP
            FROM reset_step WHERE task.id=reset_step.task_id AND task.worker_id=#{workerId}
              AND task.lease_token_hash=#{leaseHash} AND task.attempt_count=#{attempt}
            """)
    int retryReadOnly(@Param("stepId") long stepId, @Param("attempt") int attempt,
                      @Param("workerId") String workerId, @Param("leaseHash") String leaseHash);

    @Update("""
            UPDATE agent_task SET heartbeat_at=CURRENT_TIMESTAMP, lease_until=#{leaseUntil}, updated_at=CURRENT_TIMESTAMP
            WHERE id=#{taskId} AND status='RUNNING' AND worker_id=#{workerId}
              AND lease_token_hash=#{leaseHash} AND timeout_at>CURRENT_TIMESTAMP
            """)
    int heartbeat(@Param("taskId") long taskId, @Param("workerId") String workerId,
                  @Param("leaseHash") String leaseHash, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            WITH reset_steps AS (
                UPDATE agent_task_step step SET status='PENDING', attempt_count=step.attempt_count+1,
                    timeout_at=NULL, started_at=NULL, version=step.version+1, updated_at=CURRENT_TIMESTAMP
                FROM agent_task task WHERE step.task_id=task.id AND task.status='RUNNING'
                  AND task.lease_until<=CURRENT_TIMESTAMP AND task.timeout_at>CURRENT_TIMESTAMP
                  AND step.status='RUNNING' AND step.risk_level='L0' AND step.attempt_count<2
                RETURNING task.id
            )
            UPDATE agent_task task SET status='QUEUED', attempt_count=task.attempt_count+1,
                worker_id=NULL, lease_token_hash=NULL, lease_until=NULL, heartbeat_at=NULL,
                version=task.version+1, updated_at=CURRENT_TIMESTAMP
            FROM reset_steps WHERE task.id=reset_steps.id
            """)
    int recoverExpiredReadOnly();

    @Update("""
            WITH terminal AS (
                SELECT id FROM agent_task WHERE
                  (status IN ('QUEUED','RUNNING') AND timeout_at<=CURRENT_TIMESTAMP)
                  OR (status='RUNNING' AND lease_until<=CURRENT_TIMESTAMP AND
                      (attempt_count>=2 OR EXISTS (SELECT 1 FROM agent_task_step s
                       WHERE s.task_id=agent_task.id AND s.status='RUNNING' AND s.risk_level<>'L0')))
                FOR UPDATE SKIP LOCKED
            ), closed_steps AS (
                UPDATE agent_task_step step SET status='TIMED_OUT', error_code='AGENT_TIMEOUT',
                    finished_at=CURRENT_TIMESTAMP, version=step.version+1, updated_at=CURRENT_TIMESTAMP
                FROM terminal WHERE step.task_id=terminal.id AND step.status IN ('PENDING','RUNNING') RETURNING step.task_id
            )
            UPDATE agent_task task SET status='TIMED_OUT', error_code='AGENT_TIMEOUT',
                finished_at=CURRENT_TIMESTAMP, worker_id=NULL, lease_token_hash=NULL, lease_until=NULL,
                heartbeat_at=NULL, version=task.version+1, updated_at=CURRENT_TIMESTAMP
            FROM terminal WHERE task.id=terminal.id
            """)
    int closeTimedOutOrUnsafe();
}
