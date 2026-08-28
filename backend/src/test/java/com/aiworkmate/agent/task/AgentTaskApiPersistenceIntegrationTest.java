package com.aiworkmate.agent.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentTaskApiPersistenceIntegrationTest {
    private static final String PLAN_HASH = "sha256:" + "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentTaskMapper taskMapper;

    @Autowired
    private AgentTaskEventMapper eventMapper;

    @AfterEach
    void cleanTestRows() {
        jdbcTemplate.update("DELETE FROM agent_task WHERE trace_id='trace-task-api-integration'");
        jdbcTemplate.update("DELETE FROM app_user WHERE email LIKE 'agent-task-api-%@example.invalid'");
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AGENT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("AGENT_TEST_DB_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD", "postgres"));
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
