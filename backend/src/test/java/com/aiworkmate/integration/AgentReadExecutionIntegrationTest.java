package com.aiworkmate.integration;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.task.AgentTaskApiService;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.integration.FrozenReadTaskFixture.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real execution from a frozen queued plan; no LLM, handler or domain service is mocked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AgentReadExecutionIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private AgentHashing hashing;
    @Autowired private ToolCatalog catalog;
    @Autowired private AgentTaskWorker worker;
    @Autowired private AgentTaskApiService api;
    @Autowired private UserAccessService access;
    @Autowired private AgentRuntimeProperties properties;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    private long tenant, user;
    private String role;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        ENVIRONMENT.register(registry);
        // Only this disposable database runs L0 execution. Planning and all write switches stay off.
        registry.add("agent.enabled", () -> "true");
        registry.add("agent.execution-enabled", () -> "true");
        registry.add("agent.worker.poll-delay-ms", () -> "3600000");
        registry.add("agent.worker.heartbeat-delay-ms", () -> "3600000");
    }

    @BeforeEach void prepareActor() {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        role = "READ_IT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        jdbc.update("INSERT INTO rbac_role(code,name,description,tenant_id,builtin) VALUES (?,'read fixture','isolated test',?,false)", role, tenant);
        user = createUser(tenant, role);
        for (String permission : List.of("route:messages", "notification:read:self", "agent:tool:notification.mine",
                "route:todo", "todo:read", "agent:tool:todo.query")) {
            jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)", tenant, role, permission);
        }
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code IN ('notification.mine','todo.query')");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id IN ('messages','todo')", tenant);
        assertThat(properties.isWriteToolsEnabled()).isFalse();
    }

    @AfterEach void cancelUnclaimedFixtures() {
        jdbc.update("UPDATE agent_task SET status='CANCELLED' WHERE user_id=? AND status='QUEUED'", user);
    }

    @Test void afterCommitWakeupCommitsTheClaimBeforeDispatchingTheWorker() {
        var task=queue("messages","notification.mine",json.createObjectNode());
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);
        transaction.executeWithoutResult(ignored->{
            jdbc.queryForObject("SELECT 1",Integer.class);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() {
                            worker.poll();
                            String observed;
                            try {
                                observed=java.util.concurrent.CompletableFuture.supplyAsync(()->jdbc.queryForObject(
                                        "SELECT status FROM agent_task WHERE id=?",String.class,task.id())).get(5,java.util.concurrent.TimeUnit.SECONDS);
                            } catch(Exception failure) {throw new IllegalStateException("Independent claim observation failed",failure);}
                            assertThat(observed).as("claim must be committed while the original afterCommit callback is still active")
                                    .isIn("RUNNING","SUCCEEDED");
                        }
                    });
        });
        runUntil(task,"SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked AND outcome='SUCCEEDED'",Long.class,task.id())).isEqualTo(1);
    }

    @Test void workerReturnsOnlyOwnedNotificationsAndPersistsResultsEventsAndAudit() throws Exception {
        long other = createUser(tenant, role);
        notification(tenant, user, "owned notice");
        notification(tenant, other, "other user secret");
        long otherTenant = createTenant();
        notification(otherTenant, user, "other tenant secret");
        var task = queue("messages", "notification.mine", json.createObjectNode());
        runUntil(task, "SUCCEEDED");
        var detail = api.detail(actor(user), task.number());
        JsonNode result = detail.steps().get(0).result();
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("items")).hasSize(1);
        assertThat(result.path("items").get(0).path("title").asText()).isEqualTo("owned notice");
        assertThat(result.toString()).doesNotContain("other user secret", "other tenant secret", "tenantId", "userId", "businessId");
        assertThatThrownBy(() -> api.detail(actor(other), task.number())).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND decision='ALLOW' AND handler_invoked AND outcome='SUCCEEDED'", Long.class, task.id())).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT event_type FROM agent_task_event WHERE task_id=? ORDER BY id", String.class, task.id()))
                .containsSubsequence("step-started", "step-completed", "task-completed");
        assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=? AND worker_id IS NULL AND lease_token_hash IS NULL", Integer.class, task.id())).isEqualTo(1);
    }

    @Test void todoToolReturnsTheActualAssignedApplication() throws Exception {
        long applicant = createUser(tenant, role);
        long expected = todo(applicant, user);
        todo(applicant, createUser(tenant, role));
        var task = queue("todo", "todo.query", json.createObjectNode());
        runUntil(task, "SUCCEEDED");
        JsonNode result = api.detail(actor(user), task.number()).steps().get(0).result();
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("items")).hasSize(1);
        assertThat(result.path("items").get(0).path("applicationId").asLong()).isEqualTo(expected);
        assertThat(result.path("items").get(0).path("leaveType").asText()).isEqualTo("PERSONAL");
        assertThat(result.path("items").get(0).path("status").asText()).isEqualTo("PENDING");
    }

    @Test void permissionRevokedAfterQueuePreventsHandlerInvocation() {
        var task = queue("messages", "notification.mine", json.createObjectNode());
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='notification:read:self'", tenant, role);
        assertDenied(task, "REALTIME_PERMISSION_DENIED");
    }

    @Test void pageActionDisabledAfterQueuePreventsHandlerInvocation() {
        var task = queue("messages", "notification.mine", json.createObjectNode());
        jdbc.update("INSERT INTO agent_page_action_policy(tenant_id,page_id,tool_code,enabled,updated_by) VALUES (?,'messages','notification.mine',false,?)", tenant, user);
        assertDenied(task, "PAGE_ACTION_DISABLED");
    }

    @Test void changingUserTenantInvalidatesTheOldQueuedTask() {
        var task = queue("messages", "notification.mine", json.createObjectNode());
        jdbc.update("UPDATE app_user SET tenant_id=? WHERE id=?", createTenant(), user);
        assertDenied(task, "REALTIME_PERMISSION_DENIED");
    }

    @Test void changedArgumentsWithoutNewHashAreRejected() {
        var task = queue("messages", "notification.mine", json.createObjectNode());
        jdbc.update("UPDATE agent_task_step SET args='{\"size\":1}'::jsonb WHERE task_id=?", task.id());
        assertDenied(task, "SNAPSHOT_HASH_STALE");
    }

    @Test void identityFieldsCannotBeSmuggledThroughAnOtherwiseValidFrozenPlan() {
        var task = queue("messages", "notification.mine", json.createObjectNode().put("tenantId", tenant));
        assertDenied(task, "INPUT_SCHEMA_REJECTED");
    }

    @Test void platformKillSwitchIsRecheckedAfterQueue() {
        var task = queue("messages", "notification.mine", json.createObjectNode());
        jdbc.update("UPDATE agent_tool SET enabled=false WHERE tenant_id IS NULL AND code='notification.mine'");
        assertDenied(task, "TOOL_DISABLED");
    }

    private void assertDenied(Task task, String reason) {
        runUntil(task, "FAILED");
        assertThat(jdbc.queryForObject("SELECT result IS NULL FROM agent_task_step WHERE task_id=?", Boolean.class, task.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Long.class, task.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND decision_code=? AND outcome='REJECTED'", Long.class, task.id(), reason)).isEqualTo(1);
    }

    private void runUntil(Task task, String status) {
        worker.poll();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.id())).isEqualTo(status));
    }

    private Task queue(String page, String code, JsonNode arguments) {
        return new FrozenReadTaskFixture(jdbc, json, hashing, catalog)
                .create(tenant, user, page, code, arguments, FrozenReadTaskFixture.State.QUEUED);
    }

    private long createUser(long tenantId, String roleCode) {
        String name = "read-it-" + UUID.randomUUID();
        return jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id,role) VALUES (?,'not-a-real-secret',?,?,?) RETURNING id",
                Long.class, name, name + "@example.invalid", tenantId, roleCode);
    }

    private long createTenant() {
        return jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'isolated tenant') RETURNING id", Long.class, "READ_" + UUID.randomUUID().toString().replace("-", ""));
    }

    private void notification(long tenantId, long userId, String title) {
        jdbc.update("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM',?,'isolated notification')", tenantId, userId, title);
    }

    private long todo(long applicant, long approver) {
        return LeaveReadFixture.pending(jdbc, tenant, applicant, approver);
    }

    private AuthenticatedUser actor(long id) {
        var resolved = access.resolveActiveUser(id);
        return new AuthenticatedUser(id, resolved.username(), resolved.tenantId(), resolved.role(),
                resolved.roles(), resolved.permissions(), resolved.dataScopes(), resolved.permissionVersion());
    }
}
