package com.aiworkmate.agent.task;

import com.aiworkmate.integration.AbstractIsolatedAgentIntegrationTest;
import com.aiworkmate.integration.AgentIntegrationEnvironment;
import com.aiworkmate.agent.worker.AgentWorkerMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentTaskApiPersistenceIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    private static final String PLAN_HASH = "sha256:" + "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentTaskMapper taskMapper;

    @Autowired
    private AgentTaskEventMapper eventMapper;

    @Autowired private AgentWorkerMapper workerMapper;
    @Autowired private AgentTaskStepMapper stepMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanTestRows() {
        jdbcTemplate.update("DELETE FROM agent_task WHERE trace_id='trace-task-api-integration'");
        jdbcTemplate.update("DELETE FROM app_user WHERE email LIKE 'agent-task-api-%@example.invalid'");
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        ENVIRONMENT.register(registry);
    }

    @Test
    void confirmationIsSingleUseResignableAndOwnerScoped() throws Exception {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = user(tenantId, "agent-task-api-owner");
        Long otherUserId = user(tenantId, "agent-task-api-other");
        String taskNo = UUID.randomUUID().toString();
        Long taskId = waitingTask(tenantId, userId, taskNo);

        String token = "opaque-confirmation-token";
        String tokenHash = "sha256:" + "b".repeat(64);
        assertThat(taskMapper.issueConfirmation(taskId, tenantId, userId, 0L, 1, PLAN_HASH,
                tokenHash, LocalDateTime.now().plusMinutes(10))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT confirmation_token_hash FROM agent_task WHERE id=?", String.class, taskId))
                .isEqualTo(tokenHash).doesNotContain(token);

        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> consume = () -> taskMapper.consumeConfirmation(
                    tenantId, userId, taskNo, 1, PLAN_HASH, tokenHash, LocalDateTime.now().plusMinutes(1));
            List<java.util.concurrent.Future<Integer>> results = executor.invokeAll(List.of(consume, consume));
            assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(taskMapper.consumeConfirmation(
                tenantId, userId, taskNo, 1, PLAN_HASH, tokenHash, LocalDateTime.now().plusMinutes(1))).isZero();
        assertThat(taskMapper.selectOwned(tenantId, otherUserId, taskNo)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || (confirmation_token_hash IS NULL)::text FROM agent_task WHERE id=?",
                String.class, taskId)).isEqualTo("QUEUED|true");

        String resignTaskNo = UUID.randomUUID().toString();
        Long resignTaskId = waitingTask(tenantId, userId, resignTaskNo);
        String oldHash = "sha256:" + "c".repeat(64);
        String newHash = "sha256:" + "d".repeat(64);
        assertThat(taskMapper.issueConfirmation(resignTaskId, tenantId, userId, 0L, 1, PLAN_HASH,
                oldHash, LocalDateTime.now().plusMinutes(10))).isEqualTo(1);
        AgentTask signed = taskMapper.selectOwned(tenantId, userId, resignTaskNo);
        assertThat(taskMapper.issueConfirmation(resignTaskId, tenantId, userId, signed.getVersion(), 1, PLAN_HASH,
                newHash, LocalDateTime.now().plusMinutes(10))).isEqualTo(1);
        assertThat(taskMapper.consumeConfirmation(tenantId, userId, resignTaskNo, 1, PLAN_HASH,
                oldHash, LocalDateTime.now().plusMinutes(1))).isZero();
        assertThat(taskMapper.consumeConfirmation(tenantId, userId, resignTaskNo, 1, PLAN_HASH,
                newHash, LocalDateTime.now().plusMinutes(1))).isEqualTo(1);

        AgentTaskEvent first = event(resignTaskId, "snapshot", "{\"status\":\"QUEUED\"}");
        AgentTaskEvent second = event(resignTaskId, "task-completed", "{\"status\":\"SUCCEEDED\"}");
        first = eventMapper.insertEvent(first);
        second = eventMapper.insertEvent(second);
        assertThat(eventMapper.selectOwnedEvents(tenantId, userId, resignTaskNo, first.getId(), 100))
                .extracting(AgentTaskEvent::getId).containsExactly(second.getId());
        assertThat(eventMapper.selectOwnedEvents(tenantId, otherUserId, resignTaskNo, 0L, 100)).isEmpty();
        assertThat(taskMapper.selectOwnedPage(tenantId, userId, "QUEUED", null, null, 50, 0))
                .extracting(AgentTask::getTaskNo).containsExactlyInAnyOrder(taskNo, resignTaskNo);
        assertThat(taskMapper.countOwned(tenantId, userId, "QUEUED", null, null)).isEqualTo(2);
    }

    @Test
    void cancellationAndExpiryUseVersionConditionedTransitions() {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = user(tenantId, "agent-task-api-state-owner");
        String cancelTaskNo = UUID.randomUUID().toString();
        Long cancelTaskId = waitingTask(tenantId, userId, cancelTaskNo);
        assertThat(taskMapper.cancelOwned(cancelTaskId, tenantId, userId, "WAITING_CONFIRMATION", 0L)).isEqualTo(1);
        assertThat(taskMapper.cancelOwned(cancelTaskId, tenantId, userId, "WAITING_CONFIRMATION", 0L)).isZero();

        String expiredTaskNo = UUID.randomUUID().toString();
        Long expiredTaskId = waitingTask(tenantId, userId, expiredTaskNo);
        jdbcTemplate.update("UPDATE agent_task SET confirmation_token_hash=?, confirmation_expires_at=? WHERE id=?",
                "sha256:" + "e".repeat(64), Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), expiredTaskId);
        assertThat(taskMapper.expireConfirmation(expiredTaskId, 0L)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status || '|' || (confirmation_token_hash IS NULL)::text FROM agent_task WHERE id=?",
                String.class, expiredTaskId)).isEqualTo("EXPIRED|true");
    }

    @RepeatedTest(8)
    void cancellationRacingQueueClaimHasExactlyOneWinner() throws Exception {
        Long tenantId = jdbcTemplate.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        Long userId = user(tenantId, "agent-task-api-race");
        Long taskId = waitingTask(tenantId, userId, UUID.randomUUID().toString());
        jdbcTemplate.update("UPDATE agent_task SET status='QUEUED', timeout_at=CURRENT_TIMESTAMP + INTERVAL '1 minute' WHERE id=?", taskId);
        jdbcTemplate.update("""
                INSERT INTO agent_task_step(task_id,sequence_no,tool_code,tool_version,schema_hash,args,args_hash,
                    risk_level,status,trace_id)
                VALUES (?,1,'todo.query','1.0.0','schema','{}'::jsonb,'args','L0','PENDING','trace-cancel-claim-race')
                """, taskId);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var cancellation = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
                return new TransactionTemplate(transactionManager).execute(status -> {
                    int changed = taskMapper.cancelOwned(taskId, tenantId, userId, "QUEUED", 0L);
                    if (changed == 1) stepMapper.cancelPending(taskId);
                    return changed;
                });
            });
            var claim = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
                return workerMapper.claim("race-worker", "race-lease", LocalDateTime.now().plusMinutes(1), 2);
            });
            start.countDown();
            int cancelled = cancellation.get(10, TimeUnit.SECONDS);
            AgentTask claimed = claim.get(10, TimeUnit.SECONDS);
            String finalTask = jdbcTemplate.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, taskId);
            String finalStep = jdbcTemplate.queryForObject("SELECT status FROM agent_task_step WHERE task_id=?", String.class, taskId);
            if (cancelled == 1) {
                assertThat(claimed).isNull();
                assertThat(finalTask).isEqualTo("CANCELLED"); assertThat(finalStep).isEqualTo("CANCELLED");
            } else {
                assertThat(cancelled).isZero(); assertThat(claimed).isNotNull();
                assertThat(claimed.getId()).isEqualTo(taskId);
                assertThat(finalTask).isEqualTo("RUNNING"); assertThat(finalStep).isEqualTo("PENDING");
                assertThat(taskMapper.cancelOwned(taskId, tenantId, userId, "QUEUED", 0L)).isZero();
            }
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=?", Long.class, taskId)).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Long user(Long tenantId, String username) {
        String uniqueUsername = username + "-" + UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_user(username, password, email, tenant_id, role)
                VALUES (?, 'not-a-real-secret', ?, ?, 'EMPLOYEE') RETURNING id
                """, Long.class, uniqueUsername, uniqueUsername + "@example.invalid", tenantId);
    }

    private Long waitingTask(Long tenantId, Long userId, String taskNo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO agent_task(
                    task_no, tenant_id, user_id, page_id, input, page_context, plan, plan_hash,
                    plan_version, max_risk_level, status, trace_id
                ) VALUES (?, ?, ?, 'my-applications', 'draft', '{}'::jsonb, '{}'::jsonb, ?,
                          1, 'L1', 'WAITING_CONFIRMATION', 'trace-task-api-integration') RETURNING id
                """, Long.class, taskNo, tenantId, userId, PLAN_HASH);
    }

    private AgentTaskEvent event(Long taskId, String type, String payload) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setTaskId(taskId);
        event.setEventType(type);
        event.setPayload(payload);
        event.setTraceId("trace-task-event-integration");
        return event;
    }
}
