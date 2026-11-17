package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties="spring.main.allow-bean-definition-overriding=true")
@Import(ManualDecisionWorkerConfiguration.class)
class GenericAddSignAgentIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    private static final String CODE = "approval.genericTask.addSign";
    @Override protected AgentIntegrationEnvironment environment() { return ENV; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired javax.sql.DataSource datasource;
    @Autowired AgentTaskWorker worker;
    private AgentHttpTestFixture.Actor reviewer, target, owner;
    private GenericTaskReadFixture.Submission submission;
    private ConfirmedDecisionFixture driver;
    private long tenant;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        AgentBrowserFixture.registerLocal(p, ENV, AgentBrowserFixture.reserveUiPort(), SECRET);
        p.add("agent.enabled", () -> "true"); p.add("agent.execution-enabled", () -> "true");
        p.add("agent.write-tools-enabled", () -> "true");
        p.add("agent.worker.poll-delay-ms", () -> "3600000"); p.add("agent.worker.heartbeat-delay-ms", () -> "3600000");
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        reviewer = http.createAndLogin(tenant, List.of("route:todo", "todo:read", "approval:act", "agent:tool:" + CODE, "agent:tool:approval.genericTask.detail.query"));
        target = http.createAndLogin(tenant, List.of("route:todo", "todo:read", "approval:act", "agent:tool:approval.genericTask.detail.query"));
        owner = http.createAndLogin(tenant, List.of("todo:read"));
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?", tenant);
        submission = GenericTaskReadFixture.pending(jdbc, json, tenant, owner.id(), reviewer.id(), "Controlled add-sign",
                "{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}", "{\"reason\":\"PRIVATE FORM DATA\"}");
        driver = new ConfirmedDecisionFixture(jdbc, json, http, worker, hashing, catalog, tenant, "todo");
    }
    @ParameterizedTest @ValueSource(strings={"PRE", "POST"})
    void confirmedGatewayCommandAddsExactlyOneReviewerAndReturnsConcreteReceipt(String mode) throws Exception {
        enable(); var task = plan(mode, 0);
        driver.run(task, reviewer, "SUCCEEDED");
        var step = driver.detail(task, reviewer).path("steps").get(0);
        var result = step.path("result");
        assertThat(result.path("mode").asText()).isEqualTo(mode);
        assertThat(result.path("targetUserId").asLong()).isEqualTo(target.id());
        assertThat(result.path("addedTaskId").asLong()).isPositive().isNotEqualTo(submission.taskId());
        assertThat(result.path("status").asText()).isEqualTo(mode.equals("PRE") ? "WAITING" : "PENDING");
        assertThat(result.path("addedTaskStatus").asText()).isEqualTo(mode.equals("PRE") ? "PENDING" : "WAITING");
        assertThat(step.path("resultSummary").asText()).contains("#" + submission.taskId()).doesNotContain("agent.genericTask.", "PRIVATE FORM DATA");
        worker.poll(); worker.poll();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE parent_task_id=?", Integer.class, submission.taskId())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=? AND status='PENDING'", Integer.class, submission.instanceId())).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, submission.applicationId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT handler_invoked FROM agent_tool_invocation WHERE task_id=? AND decision='ALLOW'", Boolean.class, task.id())).isTrue();
    }
    @Test void migrationLeavesTheNewWriteDisabled() {
        assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?", Boolean.class, CODE)).isFalse();
        assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?", String.class, CODE)).isEqualTo(catalog.find(CODE).orElseThrow().schemaHash());
    }
    @org.junit.jupiter.api.AfterEach void restoreReleaseState() {
        jdbc.update("UPDATE agent_tool SET enabled=false WHERE tenant_id IS NULL AND code=?", CODE);
        jdbc.update("UPDATE agent_tool SET schema_hash=? WHERE tenant_id IS NULL AND code='approval.genericTask.detail.query'",
                catalog.find("approval.genericTask.detail.query").orElseThrow().schemaHash());
    }
    @Test void absentConfirmationCannotReachHandler() throws Exception {
        enable(); var task = plan("PRE", 0);
        assertThat(driver.execute(task, reviewer, null).statusCode()).isEqualTo(409);
        driver.noHandler(task); unchanged();
    }
    @Test void migrationUpgradesRecognizedReadsWithoutOverwritingReleasePolicyOrCustomContracts() {
        String candidates = "approval.genericTask.participants.query", detail = "approval.genericTask.detail.query";
        jdbc.update("UPDATE agent_tool SET enabled=true,timeout_ms=12000,schema_hash=? WHERE tenant_id IS NULL AND code=?",
                "sha256:ad1c91b9a064964b96065b9182f23b73824e23dd62966c2cb80cfbb7f4d83d95", candidates);
        String customHash = "sha256:" + "f".repeat(64);
        jdbc.update("UPDATE agent_tool SET schema_hash=? WHERE tenant_id IS NULL AND code=?", customHash, detail);
        String custom = jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code=?", String.class, detail);
        assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version='202611111010'")).isOne();
        // Only this disposable fixture replays an intentionally removed historical contract after newer migrations.
        var flyway = org.flywaydb.core.Flyway.configure().dataSource(datasource).schemas(ENV.schema()).defaultSchema(ENV.schema())
                .locations("classpath:db/migration").outOfOrder(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isOne();
        flyway.validate(); assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?", String.class, candidates)).isEqualTo(catalog.find(candidates).orElseThrow().schemaHash());
        assertThat(jdbc.queryForObject("SELECT enabled AND timeout_ms=12000 FROM agent_tool WHERE tenant_id IS NULL AND code=?", Boolean.class, candidates)).isTrue();
        assertThat(jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code=?", String.class, detail)).isEqualTo(custom);
    }
    @Test void revokedPermissionAfterQueueFailsClosed() throws Exception {
        enable(); var task = plan("POST", 0); String token = driver.confirm(task, reviewer);
        assertThat(driver.execute(task, reviewer, token).statusCode()).isEqualTo(202);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'", tenant, reviewer.role());
        driver.terminal(task, "FAILED"); driver.noHandler(task); unchanged();
    }
    @Test void staleTaskVersionCannotAddAReviewer() throws Exception {
        enable(); driver.run(plan("PRE", 1), reviewer, "FAILED"); unchanged();
    }
    @Test void protectedReadProjectsSuspendedRootAndActiveChildWithoutGrantingTheRootDecisionRights() throws Exception {
        enable(); driver.run(plan("PRE", 0), reviewer, "SUCCEEDED");
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='approval.genericTask.detail.query'");
        long child = jdbc.queryForObject("SELECT id FROM workflow_task WHERE parent_task_id=?", Long.class, submission.taskId());
        var root = readDetail(reviewer, submission.taskId());
        assertThat(root.path("status").asText()).isEqualTo("WAITING");
        assertThat(root.path("canApprove").asBoolean()).isFalse();
        assertThat(root.path("canReject").asBoolean()).isFalse();
        assertThat(root.path("canAddSign").asBoolean()).isFalse();
        var added = readDetail(target, child);
        assertThat(added.path("parentTaskId").asLong()).isEqualTo(submission.taskId());
        assertThat(added.path("addSignMode").asText()).isEqualTo("PRE");
        assertThat(added.path("canApprove").asBoolean()).isTrue();
        assertThat(added.path("canAddSign").asBoolean()).isFalse();
    }
    private com.fasterxml.jackson.databind.JsonNode readDetail(AgentHttpTestFixture.Actor actor, long taskId) throws Exception {
        var task = new FrozenReadTaskFixture(jdbc, json, hashing, catalog).create(tenant, actor.id(), "todo", "approval.genericTask.detail.query",
                json.createObjectNode().put("taskId", taskId), FrozenReadTaskFixture.State.QUEUED);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(25)).until(() -> {
            worker.poll(); return !List.of("QUEUED", "RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.id()));
        });
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, task.id())).isEqualTo("SUCCEEDED");
        return json.readTree(jdbc.queryForObject("SELECT result::text FROM agent_task_step WHERE task_id=?", String.class, task.id()));
    }
    private void enable() { jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?", CODE); }
    private FrozenWriteTaskFixture.Task plan(String mode, int version) {
        return new FrozenWriteTaskFixture(jdbc, json, hashing, catalog).create(tenant, reviewer.id(), "todo", CODE,
                json.createObjectNode().put("taskId", submission.taskId()).put("targetUserId", target.id()).put("version", version).put("mode", mode).put("reason", "Explicit additional review"));
    }
    private void unchanged() {
        assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?", Integer.class, submission.taskId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE parent_task_id=?", Integer.class, submission.taskId())).isZero();
    }
}
