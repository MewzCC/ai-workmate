package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.worker.AgentWorkerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class GatewayPersistenceIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GatewayExecutionSnapshotMapper snapshotMapper;

    @Autowired
    private GatewayAuditWriter auditWriter;

    @Autowired
    private AgentWorkerMapper workerMapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AGENT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("AGENT_TEST_DB_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD", "postgres"));
    }

    @Test
    void shouldReloadSnapshotReserveBudgetAndPersistAppendOnlyAudit() {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO app_user(username, password, email, tenant_id)
                VALUES ('gateway-integration-user', 'not-a-real-secret', 'gateway-integration@example.invalid', ?)
                RETURNING id
                """, Long.class, tenantId);
        Long taskId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(
                    task_no, tenant_id, user_id, page_id, input, page_context, plan, plan_hash,
                    status, worker_id, lease_token_hash, lease_until, timeout_at, trace_id
                ) VALUES (
                    '0191f69c-7a33-7b45-9c62-a07f82d8a006', ?, ?, 'todo-list', 'query', '{}'::jsonb,
                    '{"planVersion":1,"steps":[]}'::jsonb, 'sha256:plan', 'RUNNING', 'worker-1',
                    'sha256:lease', ?, ?, 'trace-gateway-integration'
                ) RETURNING id
                """, Long.class, tenantId, userId,
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(1)),
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(1)));
        Long stepId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task_step(
                    task_id, sequence_no, tool_code, tool_version, schema_hash, args, args_hash,
                    risk_level, status, timeout_at, trace_id
                ) VALUES (?, 1, 'todo.query', '1.0.0', 'sha256:schema', '{"limit":10}'::jsonb,
                          'sha256:args', 'L0', 'RUNNING', ?, 'trace-gateway-integration')
                RETURNING id
                """, Long.class, taskId, Timestamp.valueOf(LocalDateTime.now().plusSeconds(15)));

        GatewayExecutionSnapshot snapshot = snapshotMapper.selectSnapshot(stepId);
        assertThat(snapshot.getTaskId()).isEqualTo(taskId);
        assertThat(snapshot.getUserId()).isEqualTo(userId);
        assertThat(snapshot.getArguments()).contains("\"limit\": 10");
        assertThat(snapshotMapper.reserveToolCall(
                taskId, stepId, "worker-1", "sha256:lease", 0, 1
        )).isEqualTo(1);
        assertThat(snapshotMapper.reserveToolCall(
                taskId, stepId, "worker-1", "sha256:lease", 0, 1
        )).isZero();

        String decisionId = auditWriter.record(snapshot, GatewayDecision.ALLOW, "POLICY_ALLOWED", false);
        auditWriter.complete(decisionId, true, "SUCCEEDED", 12, null, 1);
        Integer rows = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM agent_tool_invocation
                WHERE decision_id = ? AND handler_invoked AND outcome = 'SUCCEEDED'
                """, Integer.class, decisionId);
        assertThat(rows).isEqualTo(1);

        Long queuedTaskId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(
                    task_no, tenant_id, user_id, page_id, input, page_context, plan, plan_hash,
                    status, timeout_at, trace_id
                ) VALUES (
                    '0191f69c-7a33-7b45-9c62-a07f82d8a007', ?, ?, 'todo-list', 'query', '{}'::jsonb,
                    '{"planVersion":1,"steps":[]}'::jsonb, 'sha256:plan', 'QUEUED', ?,
                    'trace-worker-integration'
                ) RETURNING id
                """, Long.class, tenantId, userId,
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(1)));
        Long queuedStepId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task_step(
                    task_id, sequence_no, tool_code, tool_version, schema_hash, args, args_hash,
                    risk_level, status, trace_id
                ) VALUES (?, 1, 'todo.query', '1.0.0', 'sha256:schema', '{}'::jsonb,
                          'sha256:args', 'L0', 'PENDING', 'trace-worker-integration') RETURNING id
                """, Long.class, queuedTaskId);

        var claimed = workerMapper.claim("worker-2", "sha256:new-lease",
                LocalDateTime.now().plusSeconds(30), 2);
        assertThat(claimed.getId()).isEqualTo(queuedTaskId);
        assertThat(workerMapper.claim("worker-3", "sha256:blocked",
                LocalDateTime.now().plusSeconds(30), 2)).isNull();
        assertThat(workerMapper.startNextStep(queuedTaskId, "worker-2", "sha256:new-lease", 0,
                LocalDateTime.now().plusSeconds(15)).getId()).isEqualTo(queuedStepId);
        jdbcTemplate.update("UPDATE agent_task SET lease_until=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), queuedTaskId);
        assertThat(workerMapper.closeTimedOutOrUnsafe()).isZero();
        assertThat(workerMapper.recoverExpiredReadOnly()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || attempt_count FROM agent_task WHERE id=?",
                String.class, queuedTaskId)).isEqualTo("QUEUED|1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || attempt_count FROM agent_task_step WHERE id=?",
                String.class, queuedStepId)).isEqualTo("PENDING|1");
        jdbcTemplate.update("UPDATE agent_task_step SET status='SUCCEEDED' WHERE id=?", queuedStepId);
        jdbcTemplate.update("UPDATE agent_task SET status='SUCCEEDED', finished_at=CURRENT_TIMESTAMP WHERE id=?",
                queuedTaskId);

        Long unsafeTaskId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(
                    task_no, tenant_id, user_id, page_id, input, page_context, plan, plan_hash,
                    status, timeout_at, trace_id
                ) VALUES (
                    '0191f69c-7a33-7b45-9c62-a07f82d8a008', ?, ?, 'leave-list', 'draft', '{}'::jsonb,
                    '{"planVersion":1,"steps":[]}'::jsonb, 'sha256:unsafe-plan', 'QUEUED', ?,
                    'trace-worker-unsafe-integration'
                ) RETURNING id
                """, Long.class, tenantId, userId,
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(1)));
        Long unsafeStepId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task_step(
                    task_id, sequence_no, tool_code, tool_version, schema_hash, args, args_hash,
                    risk_level, status, trace_id
                ) VALUES (?, 1, 'leave.createDraft', '1.0.0', 'sha256:unsafe-schema', '{}'::jsonb,
                          'sha256:unsafe-args', 'L1', 'PENDING', 'trace-worker-unsafe-integration') RETURNING id
                """, Long.class, unsafeTaskId);
        var unsafeClaim = workerMapper.claim("worker-unsafe", "sha256:unsafe-lease",
                LocalDateTime.now().plusSeconds(30), 2);
        assertThat(unsafeClaim.getId()).isEqualTo(unsafeTaskId);
        assertThat(workerMapper.startNextStep(unsafeTaskId, "worker-unsafe", "sha256:unsafe-lease", 0,
                LocalDateTime.now().plusSeconds(15)).getId()).isEqualTo(unsafeStepId);
        jdbcTemplate.update("UPDATE agent_task SET lease_until=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), unsafeTaskId);
        assertThat(workerMapper.closeTimedOutOrUnsafe()).isEqualTo(1);
        assertThat(workerMapper.recoverExpiredReadOnly()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM agent_task WHERE id=?", String.class, unsafeTaskId)).isEqualTo("TIMED_OUT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM agent_task_step WHERE id=?", String.class, unsafeStepId)).isEqualTo("TIMED_OUT");

        Long unstartedTaskId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(
                    task_no, tenant_id, user_id, page_id, input, page_context, plan, plan_hash,
                    status, timeout_at, trace_id
                ) VALUES (
                    '0191f69c-7a33-7b45-9c62-a07f82d8a009', ?, ?, 'todo-list', 'query', '{}'::jsonb,
                    '{"planVersion":1,"steps":[]}'::jsonb, 'sha256:unstarted-plan', 'QUEUED', ?,
                    'trace-worker-unstarted-integration'
                ) RETURNING id
                """, Long.class, tenantId, userId,
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(1)));
        Long unstartedStepId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task_step(
                    task_id, sequence_no, tool_code, tool_version, schema_hash, args, args_hash,
                    risk_level, status, trace_id
                ) VALUES (?, 1, 'todo.query', '1.0.0', 'sha256:unstarted-schema', '{}'::jsonb,
                          'sha256:unstarted-args', 'L0', 'PENDING', 'trace-worker-unstarted-integration') RETURNING id
                """, Long.class, unstartedTaskId);
        assertThat(workerMapper.claim("worker-rejected", "sha256:rejected-lease",
                LocalDateTime.now().plusSeconds(30), 2).getId()).isEqualTo(unstartedTaskId);
        jdbcTemplate.update("UPDATE agent_task SET lease_until=? WHERE id=?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), unstartedTaskId);
        assertThat(workerMapper.closeTimedOutOrUnsafe()).isZero();
        assertThat(workerMapper.recoverExpiredReadOnly()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || attempt_count FROM agent_task WHERE id=?",
                String.class, unstartedTaskId)).isEqualTo("QUEUED|1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || attempt_count FROM agent_task_step WHERE id=?",
                String.class, unstartedStepId)).isEqualTo("PENDING|0");
    }
}
