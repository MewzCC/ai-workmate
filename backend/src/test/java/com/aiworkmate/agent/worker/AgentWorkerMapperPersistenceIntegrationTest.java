package com.aiworkmate.agent.worker;

import com.aiworkmate.integration.AbstractIsolatedAgentIntegrationTest;
import com.aiworkmate.integration.AgentIntegrationEnvironment;
import com.aiworkmate.agent.task.AgentTask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentWorkerMapperPersistenceIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentWorkerMapper mapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        ENVIRONMENT.register(registry);
        registry.add("agent.enabled", () -> "false");
        registry.add("agent.execution-enabled", () -> "false");
    }

    @Test
    @Transactional
    @Rollback
    void completesStepWithQualifiedOptimisticVersionOnPostgresql() {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = createTestUser(tenantId);
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
        Long userId = createTestUser(tenantId);
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
        Long userId = createTestUser(tenantId);
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
    enum Transition { COMPLETE, FAIL, UNKNOWN, RETRY }
    enum StaleScope { WRONG_HASH, WRONG_WORKER, EXPIRED_LEASE, EXPIRED_TASK, TERMINAL_TASK, TASK_ATTEMPT, STEP_ATTEMPT }
    private record Fixture(long taskId, long stepId) { }
    private static final String OWNER = "worker-transition-owner";
    private static final String HASH = "sha256:" + "d".repeat(64);

    static Stream<Arguments> staleTransitions() {
        return Arrays.stream(Transition.values()).flatMap(action ->
                Arrays.stream(StaleScope.values()).map(scope -> Arguments.of(action, scope)));
    }

    @ParameterizedTest
    @MethodSource("staleTransitions")
    @Transactional
    @Rollback
    void staleWorkerCannotChangeEitherTaskOrStep(Transition action, StaleScope scope) {
        Fixture fixture = runningFixture(action == Transition.UNKNOWN ? "L2" : "L0");
        switch (scope) {
            case EXPIRED_LEASE -> jdbcTemplate.update("UPDATE agent_task SET lease_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?", fixture.taskId());
            case EXPIRED_TASK -> jdbcTemplate.update("UPDATE agent_task SET timeout_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?", fixture.taskId());
            case TERMINAL_TASK -> jdbcTemplate.update("UPDATE agent_task SET status='TIMED_OUT' WHERE id=?", fixture.taskId());
            case TASK_ATTEMPT -> jdbcTemplate.update("UPDATE agent_task SET attempt_count=1 WHERE id=?", fixture.taskId());
            case STEP_ATTEMPT -> jdbcTemplate.update("UPDATE agent_task_step SET attempt_count=1 WHERE id=?", fixture.stepId());
            default -> { }
        }
        String before = snapshot(fixture);
        String worker = scope == StaleScope.WRONG_WORKER ? "old-worker" : OWNER;
        String hash = scope == StaleScope.WRONG_HASH ? "old-lease" : HASH;

        assertThat(transition(action, fixture, worker, hash, 0)).isZero();
        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(Transition.class)
    @Transactional
    @Rollback
    void currentLeaseCanCompleteItsAtomicTransition(Transition action) {
        Fixture fixture = runningFixture(action == Transition.UNKNOWN ? "L2" : "L0");
        assertThat(transition(action, fixture, OWNER, HASH, 0)).isEqualTo(1);
        String expectedTask = switch (action) {
            case COMPLETE -> "RUNNING";
            case FAIL -> "FAILED";
            case UNKNOWN -> "PARTIALLY_SUCCEEDED";
            case RETRY -> "QUEUED";
        };
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, fixture.taskId())).isEqualTo(expectedTask);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM agent_task_step WHERE id=?", Long.class, fixture.stepId())).isEqualTo(1L);
    }

    @Test
    @Transactional
    @Rollback
    void expiredLeaseCannotBeRevivedOrReportCompletion() {
        Fixture fixture = runningFixture("L0");
        jdbcTemplate.update("UPDATE agent_task SET lease_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?", fixture.taskId());
        jdbcTemplate.update("UPDATE agent_task_step SET status='PENDING' WHERE id=?", fixture.stepId());
        String before = snapshot(fixture);
        assertThat(mapper.heartbeat(fixture.taskId(), OWNER, HASH, LocalDateTime.now().plusMinutes(1))).isZero();
        assertThat(mapper.startNextStep(fixture.taskId(), OWNER, HASH, 0, LocalDateTime.now().plusSeconds(10))).isNull();
        assertThat(snapshot(fixture)).isEqualTo(before);
        jdbcTemplate.update("UPDATE agent_task_step SET status='SUCCEEDED' WHERE id=?", fixture.stepId());
        before = snapshot(fixture);
        assertThat(mapper.completeTask(fixture.taskId(), OWNER, HASH)).isZero();
        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    @Test
    @Transactional
    @Rollback
    void recoveryRejectsOldWorkerAndNewLeaseFinishesOnlyReadStep() {
        Fixture fixture = runningFixture("L0");
        jdbcTemplate.update("UPDATE agent_task SET lease_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?", fixture.taskId());
        assertThat(mapper.recoverExpiredReadOnly()).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status || '|' || attempt_count FROM agent_task WHERE id=?", String.class, fixture.taskId())).isEqualTo("QUEUED|1");
        assertThat(jdbcTemplate.queryForObject("SELECT status || '|' || attempt_count FROM agent_task_step WHERE id=?", String.class, fixture.stepId())).isEqualTo("PENDING|1");
        AgentTask claimed = mapper.claim("replacement-worker", "replacement-lease", LocalDateTime.now().plusMinutes(1), 1);
        assertThat(claimed).isNotNull();
        assertThat(claimed.getId()).isEqualTo(fixture.taskId());
        assertThat(mapper.startNextStep(fixture.taskId(), "replacement-worker", "replacement-lease", 1, LocalDateTime.now().plusSeconds(20))).isNotNull();
        String before = snapshot(fixture);
        for (Transition action : Transition.values()) assertThat(transition(action, fixture, OWNER, HASH, 0)).isZero();
        assertThat(snapshot(fixture)).isEqualTo(before);
        assertThat(mapper.completeStep(fixture.stepId(), 1, "replacement-worker", "replacement-lease", "{\"items\":[]}")).isEqualTo(1);
        assertThat(mapper.completeTask(fixture.taskId(), "replacement-worker", "replacement-lease")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, fixture.taskId())).isEqualTo("SUCCEEDED");
    }

    private int transition(Transition action, Fixture fixture, String worker, String hash, int attempt) {
        return switch (action) {
            case COMPLETE -> mapper.completeStep(fixture.stepId(), attempt, worker, hash, "{\"items\":[]}");
            case FAIL -> mapper.fail(fixture.stepId(), attempt, worker, hash, "TEST_REJECTION", "Synthetic rejection");
            case UNKNOWN -> mapper.markOutcomeUnknown(fixture.stepId(), attempt, worker, hash);
            case RETRY -> mapper.retryReadOnly(fixture.stepId(), attempt, worker, hash);
        };
    }

    private String snapshot(Fixture fixture) {
        return jdbcTemplate.queryForObject("SELECT to_jsonb(task)::text FROM agent_task task WHERE id=?", String.class, fixture.taskId())
                + jdbcTemplate.queryForObject("SELECT to_jsonb(step)::text FROM agent_task_step step WHERE id=?", String.class, fixture.stepId());
    }

    private Fixture runningFixture(String risk) {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = createTestUser(tenantId);
        Long taskId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(task_no,tenant_id,user_id,page_id,input,page_context,status,
                    worker_id,lease_token_hash,lease_until,timeout_at,trace_id)
                VALUES (?,?,?,'dashboard','query','{}'::jsonb,'RUNNING',?,?,
                    CURRENT_TIMESTAMP + INTERVAL '1 minute',CURRENT_TIMESTAMP + INTERVAL '2 minutes','trace-transition') RETURNING id
                """, Long.class, UUID.randomUUID().toString(), tenantId, userId, OWNER, HASH);
        Long stepId = jdbcTemplate.queryForObject("""
                INSERT INTO agent_task_step(task_id,sequence_no,tool_code,tool_version,schema_hash,args,args_hash,
                    risk_level,status,attempt_count,timeout_at,trace_id)
                VALUES (?,1,'todo.query','1.0.0','schema','{}'::jsonb,'args',?,'RUNNING',0,
                    CURRENT_TIMESTAMP + INTERVAL '30 seconds','trace-transition-step') RETURNING id
                """, Long.class, taskId, risk);
        return new Fixture(taskId, stepId);
    }

    private Long createTestUser(Long tenantId) {
        String username = "agent-worker-it-" + UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user(username,password,email,tenant_id)
                VALUES (?, 'not-a-real-secret', ?, ?) RETURNING id
                """, Long.class, username, username + "@example.invalid", tenantId);
    }
}
