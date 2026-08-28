package com.aiworkmate.agent.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentWorkerMapperPersistenceIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentWorkerMapper mapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AGENT_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("AGENT_TEST_DB_USERNAME", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD", "postgres"));
        registry.add("agent.enabled", () -> "false");
        registry.add("agent.execution-enabled", () -> "false");
    }

    @Test
    @Transactional
    @Rollback
    void completesStepWithQualifiedOptimisticVersionOnPostgresql() {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE tenant_id=? ORDER BY id LIMIT 1", Long.class, tenantId);
        String workerId = "worker-sql-regression";
        String leaseHash = "sha256:" + "a".repeat(64);
        Long taskId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(
                    task_no, tenant_id, user_id, page_id, input, page_context, status,
                    worker_id, lease_token_hash, lease_until, timeout_at, trace_id
                ) VALUES (?, ?, ?, 'dashboard', 'query', '{}'::jsonb, 'RUNNING',
                          ?, ?, ?, ?, 'trace-worker-sql-regression') RETURNING id
                """, Long.class, UUID.randomUUID().toString(), tenantId, userId, workerId, leaseHash,
                LocalDateTime.now().plusMinutes(1), LocalDateTime.now().plusMinutes(1));
        Long stepId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task_step(
                    task_id, sequence_no, tool_code, tool_version, schema_hash, args, args_hash,
                    risk_level, status, attempt_count, timeout_at, trace_id
                ) VALUES (?, 1, 'todo.query', '1.0.0', 'schema', '{}'::jsonb, 'args',
                          'L0', 'RUNNING', 0, ?, 'trace-worker-step-sql-regression') RETURNING id
                """, Long.class, taskId, LocalDateTime.now().plusSeconds(30));

        assertThat(mapper.completeStep(stepId, 0, workerId, leaseHash, "{\"items\":[]}"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || version || '|' || (result->>'items') FROM agent_task_step WHERE id=?",
                String.class, stepId)).isEqualTo("SUCCEEDED|1|[]");
    }

    @Test
    @Transactional
    @Rollback
    void closesUnknownWriteOutcomeWithoutRetryOnPostgresql() {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE tenant_id=? ORDER BY id LIMIT 1", Long.class, tenantId);
        String workerId = "worker-write-unknown";
        String leaseHash = "sha256:" + "b".repeat(64);
        Long taskId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(
                    task_no, tenant_id, user_id, page_id, input, page_context, status,
                    worker_id, lease_token_hash, lease_until, timeout_at, trace_id
                ) VALUES (?, ?, ?, 'my-applications', 'submit', '{}'::jsonb, 'RUNNING',
                          ?, ?, ?, ?, 'trace-write-unknown') RETURNING id
                """, Long.class, UUID.randomUUID().toString(), tenantId, userId, workerId, leaseHash,
                LocalDateTime.now().plusMinutes(1), LocalDateTime.now().plusMinutes(1));
        Long stepId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task_step(
                    task_id, sequence_no, tool_code, tool_version, schema_hash, args, args_hash,
                    risk_level, status, attempt_count, timeout_at, trace_id
                ) VALUES (?, 1, 'leave.submit', '1.0.0', 'schema', '{}'::jsonb, 'args',
                          'L2', 'RUNNING', 0, ?, 'trace-write-unknown-step') RETURNING id
                """, Long.class, taskId, LocalDateTime.now().plusSeconds(30));

        assertThat(mapper.markOutcomeUnknown(stepId, 0, workerId, leaseHash)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || error_code FROM agent_task WHERE id=?", String.class, taskId))
                .isEqualTo("PARTIALLY_SUCCEEDED|TOOL_RESULT_UNKNOWN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || error_code FROM agent_task_step WHERE id=?", String.class, stepId))
                .isEqualTo("FAILED|TOOL_RESULT_UNKNOWN");
        assertThat(mapper.retryReadOnly(stepId, 0, workerId, leaseHash)).isZero();
    }

    @Test
    @Transactional
    @Rollback
    void expiredWriteLeaseBecomesPartialInsteadOfBeingReplayedAfterRestart() {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE tenant_id=? ORDER BY id LIMIT 1", Long.class, tenantId);
        Long taskId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(
                    task_no, tenant_id, user_id, page_id, input, page_context, status,
                    worker_id, lease_token_hash, lease_until, timeout_at, trace_id
                ) VALUES (?, ?, ?, 'my-applications', 'submit', '{}'::jsonb, 'RUNNING',
                          'dead-worker', ?, ?, ?, 'trace-restart-unknown') RETURNING id
                """, Long.class, UUID.randomUUID().toString(), tenantId, userId,
                "sha256:" + "c".repeat(64), LocalDateTime.now().minusSeconds(1),
                LocalDateTime.now().plusMinutes(1));
        jdbcTemplate.update("""
                INSERT INTO agent_task_step(
                    task_id, sequence_no, tool_code, tool_version, schema_hash, args, args_hash,
                    risk_level, status, attempt_count, timeout_at, trace_id
                ) VALUES (?, 1, 'leave.submit', '1.0.0', 'schema', '{}'::jsonb, 'args',
                          'L2', 'RUNNING', 0, ?, 'trace-restart-unknown-step')
                """, taskId, LocalDateTime.now().plusSeconds(30));

        assertThat(mapper.closeTimedOutOrUnsafe()).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || error_code FROM agent_task WHERE id=?", String.class, taskId))
                .isEqualTo("PARTIALLY_SUCCEEDED|TOOL_RESULT_UNKNOWN");
        mapper.recoverExpiredReadOnly();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM agent_task WHERE id=?", String.class, taskId))
                .isEqualTo("PARTIALLY_SUCCEEDED");
    }
}
