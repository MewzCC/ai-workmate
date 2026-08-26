package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskMapper;
import com.aiworkmate.agent.task.AgentTaskStep;
import com.aiworkmate.agent.task.AgentTaskStepMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentPlanningPersistenceIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentTaskMapper taskMapper;
    @Autowired private AgentTaskStepMapper stepMapper;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AGENT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("AGENT_TEST_DB_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD", "postgres"));
        registry.add("agent.enabled", () -> "false");
    }

    @Test
    void persistsJsonbPlanBindingsAndQueuesByVersionedHash() {
        Long tenantId = jdbc.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        String username = "planner-it-" + UUID.randomUUID();
        Long userId = jdbc.queryForObject("""
                INSERT INTO app_user(tenant_id, username, email, password, role, status)
                VALUES (?, ?, ?, 'not-a-login-password', 'EMPLOYEE', 1) RETURNING id
                """, Long.class, tenantId, username, username + "@example.invalid");

        AgentTask task = new AgentTask();
        task.setTaskNo(UUID.randomUUID().toString()); task.setTenantId(tenantId); task.setUserId(userId);
        task.setPageId("todo-list"); task.setInput("query todos"); task.setPageContext("{\"status\":\"PENDING\"}");
        task.setPlanVersion(1); task.setMaxRiskLevel("L0"); task.setStatus("RECEIVED");
        task.setAttemptCount(0); task.setToolCallCount(0); task.setTraceId(UUID.randomUUID().toString()); task.setVersion(0L);
        assertThat(taskMapper.insertReceived(task)).isEqualTo(1);
        assertThat(task.getId()).isNotNull();
        assertThat(taskMapper.transition(task.getId(), "RECEIVED", "PLANNING", 0L)).isEqualTo(1);

        String argsHash = "sha256:" + "b".repeat(64);
        String schemaHash = "sha256:" + "c".repeat(64);
        String plan = "{\"planVersion\":1,\"summary\":\"query\",\"steps\":[{\"sequence\":1,\"toolCode\":\"todo.query\",\"toolVersion\":\"1.0.0\",\"schemaHash\":\"" + schemaHash + "\",\"argsHash\":\"" + argsHash + "\",\"riskLevel\":\"L0\",\"sideEffect\":\"NONE\",\"confirmationPolicy\":\"NONE\"}]}";
        String planHash = "sha256:" + "a".repeat(64);
        assertThat(taskMapper.finalizePlan(task.getId(), 1L, plan, planHash, "L0", "PLAN_READY",
                "stub", "phase2a-v1", 10L, 1)).isEqualTo(1);

        AgentTaskStep step = new AgentTaskStep();
        step.setTaskId(task.getId()); step.setSequenceNo(1); step.setToolCode("todo.query");
        step.setToolVersion("1.0.0"); step.setSchemaHash(schemaHash); step.setArgs("{\"page\":1,\"size\":20}");
        step.setArgsHash(argsHash); step.setRiskLevel("L0"); step.setStatus("PENDING");
        step.setAttemptCount(0); step.setTraceId(task.getTraceId()); step.setVersion(0L);
        assertThat(stepMapper.insertPending(step)).isEqualTo(1);
        assertThat(step.getId()).isNotNull();
        assertThat(taskMapper.queuePlanReady(task.getId(), tenantId, userId, 1, planHash,
                LocalDateTime.now().plusMinutes(1))).isEqualTo(1);
        assertThat(taskMapper.queuePlanReady(task.getId(), tenantId, userId, 1, "sha256:" + "d".repeat(64),
                LocalDateTime.now().plusMinutes(1))).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.getId()))
                .isEqualTo("QUEUED");
    }
}
