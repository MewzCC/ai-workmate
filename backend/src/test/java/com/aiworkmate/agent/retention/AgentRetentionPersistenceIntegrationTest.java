package com.aiworkmate.agent.retention;

import com.aiworkmate.agent.gateway.AgentAuditQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentRetentionPersistenceIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentRetentionCleaner cleaner;
    @Autowired private AgentAuditQueryService auditQuery;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AGENT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("AGENT_TEST_DB_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD", "postgres"));
        registry.add("agent.retention-batch-size", () -> "10");
    }

    @Test
    @Transactional
    void auditIsOwnerScopedAndRetentionKeepsActiveAndRecentTasks() {
        long tenantId = jdbc.queryForObject("SELECT id FROM tenant ORDER BY id LIMIT 1", Long.class);
        long ownerId = user(tenantId, "retention-owner");
        long otherId = user(tenantId, "retention-other");
        long dueTask = task(tenantId, ownerId, "0191f69c-7a33-7b45-9c62-a07f82d8a031", "SUCCEEDED", 100);
        long detailOnlyTask = task(tenantId, ownerId, "0191f69c-7a33-7b45-9c62-a07f82d8a032", "SUCCEEDED", 40);
        long activeTask = task(tenantId, ownerId, "0191f69c-7a33-7b45-9c62-a07f82d8a033", "RUNNING", 100);
        long recentTask = task(tenantId, ownerId, "0191f69c-7a33-7b45-9c62-a07f82d8a034", "SUCCEEDED", 2);
        addDetails(dueTask, tenantId, ownerId, 35, "00000000-0000-4000-8000-000000000031");
        addDetails(detailOnlyTask, tenantId, ownerId, 35, "00000000-0000-4000-8000-000000000032");

        assertThat(auditQuery.findOwnedTaskAudit(
                tenantId, ownerId, "0191f69c-7a33-7b45-9c62-a07f82d8a032", 10)).hasSize(1);
        assertThat(auditQuery.findOwnedTaskAudit(
                tenantId, otherId, "0191f69c-7a33-7b45-9c62-a07f82d8a032", 10)).isEmpty();
        assertThat(auditQuery.findOwnedTaskAudit(
                tenantId + 99999, ownerId, "0191f69c-7a33-7b45-9c62-a07f82d8a032", 10)).isEmpty();

        cleaner.clean();

        assertThat(count("agent_task", dueTask)).isZero();
        assertThat(count("agent_task", detailOnlyTask)).isOne();
        assertThat(count("agent_task", activeTask)).isOne();
        assertThat(count("agent_task", recentTask)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task_event WHERE task_id=?",
                Integer.class, detailOnlyTask)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=?",
                Integer.class, detailOnlyTask)).isZero();
    }

    private long user(long tenantId, String username) {
        return jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,tenant_id)
                VALUES (?, 'not-a-real-secret', ? || '@example.invalid', ?) RETURNING id
                """, Long.class, username, username, tenantId);
    }

    private long task(long tenantId, long userId, String taskNo, String status, int ageDays) {
        LocalDateTime created = LocalDateTime.now().minusDays(ageDays);
        return jdbc.queryForObject("""
                INSERT INTO agent_task(task_no,tenant_id,user_id,page_id,input,page_context,status,
                    timeout_at,trace_id,created_at,updated_at)
                VALUES (?, ?, ?, 'todo-list', 'query', '{}'::jsonb, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, taskNo, tenantId, userId, status,
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)), "trace-retention-" + taskNo,
                Timestamp.valueOf(created), Timestamp.valueOf(created));
    }

    private void addDetails(long taskId, long tenantId, long userId, int ageDays, String decisionId) {
        long stepId = jdbc.queryForObject("""
                INSERT INTO agent_task_step(task_id,sequence_no,tool_code,tool_version,schema_hash,args,args_hash,
                    risk_level,status,trace_id) VALUES (?,1,'todo.query','1.0.0','schema','{}'::jsonb,'args',
                    'L0','SUCCEEDED','trace-retention-step') RETURNING id
                """, Long.class, taskId);
        LocalDateTime created = LocalDateTime.now().minusDays(ageDays);
        jdbc.update("""
                INSERT INTO agent_task_event(task_id,event_type,payload,trace_id,created_at)
                VALUES (?,'task-completed','{}'::jsonb,'trace-retention-event',?)
                """, taskId, Timestamp.valueOf(created));
        jdbc.update("""
                INSERT INTO agent_tool_invocation(decision_id,tenant_id,user_id,task_id,step_id,attempt,
                    tool_code,tool_version,decision,decision_code,args_hash,handler_invoked,outcome,trace_id,
                    started_at,completed_at,duration_ms)
                VALUES (?,?,?,?,?,0,'todo.query','1.0.0','ALLOW','ALLOWED','args',true,'SUCCEEDED',
                    'trace-retention-audit',?,?,4)
                """, decisionId, tenantId, userId, taskId, stepId,
                Timestamp.valueOf(created), Timestamp.valueOf(created.plusSeconds(1)));
    }

    private Integer count(String table, long id) {
        if (!"agent_task".equals(table)) throw new IllegalArgumentException("unsupported table");
        return jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE id=?", Integer.class, id);
    }
}
