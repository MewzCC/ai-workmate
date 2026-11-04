package com.aiworkmate.integration;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.service.LeaveWorkflowService;
import com.aiworkmate.service.model.BusinessCandidateQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real HTTP, SQL and Worker/Gateway checks against owned PostgreSQL and Redis only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApprovalParticipantPagingIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired LeaveWorkflowService service;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired AgentTaskWorker worker;
    @Autowired AgentRuntimeProperties runtime;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor actor, applicant;
    private long tenant, application, task, instance;
    private String marker, approverRole, readerRole;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        AgentBrowserFixture.registerLocal(p, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
        p.add("agent.enabled", () -> "true"); p.add("agent.execution-enabled", () -> "true");
        p.add("agent.worker.poll-delay-ms", () -> "3600000"); p.add("agent.worker.heartbeat-delay-ms", () -> "3600000");
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        actor = http.createAndLogin(tenant, List.of("approval:act", "route:approval-list", "agent:tool:approval.task.participants.query"));
        applicant = http.createAndLogin(tenant, List.of("approval:act"));
        marker = "part_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbc.update("UPDATE app_user SET display_name=? WHERE id=?", marker + " owner", applicant.id());
        jdbc.update("UPDATE app_user SET display_name=? WHERE id=?", marker + " reviewer", actor.id());
        application = LeaveReadFixture.pending(jdbc, tenant, applicant.id(), actor.id());
        task = jdbc.queryForObject("SELECT id FROM workflow_task WHERE tenant_id=? AND business_type='LEAVE_APPLICATION' AND business_id=?", Long.class, tenant, application);
        instance = jdbc.queryForObject("SELECT instance_id FROM workflow_task WHERE id=?", Long.class, task);
        approverRole = role(true); readerRole = role(false);
        users(readerRole, "reader", 60); users(approverRole, "approver", 65);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id='approval-list'", tenant);
    }
    private String role(boolean approve) {
        String code = "PART_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(java.util.Locale.ROOT);
        jdbc.update("INSERT INTO rbac_role(tenant_id,code,name,description,builtin) VALUES (?,?,?,'Synthetic participant role',false)", tenant, code, code);
        if (approve) jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,'approval:act')", tenant, code);
        return code;
    }
    private void users(String role, String label, int count) {
        jdbc.update("INSERT INTO app_user(tenant_id,username,display_name,password,role) SELECT ?,? || '-" + label + "-' || LPAD(i::text,3,'0'),? || ' ' || ? || ' ' || i,'synthetic-no-login',? FROM generate_series(1,?) i",
                tenant, marker, marker, label, role, count);
        jdbc.update("INSERT INTO user_role(tenant_id,user_id,role_code) SELECT tenant_id,id,role FROM app_user WHERE tenant_id=? AND username LIKE ?", tenant, marker + "-" + label + "-%");
    }
    private JsonNode get(AgentHttpTestFixture.Actor user, long taskId, String query, int expected) throws Exception {
        var response = AgentHttpTestFixture.client().send(http.request("GET", "/api/approval-tasks/" + taskId + "/participant-options?" + query, user, null, null).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("candidate status").isEqualTo(expected);
        return json.readTree(response.body()).path("data");
    }
    private String query(boolean approvers, int page) { return "keyword=" + marker + "&approversOnly=" + approvers + "&page=" + page + "&size=20"; }
    @Test void filtersEligibilityBeforePaginationAndCanReadBeyondTheFormerFiftyUserLimit() throws Exception {
        var ids = new LinkedHashSet<Long>();
        for (int page = 1; page <= 4; page++) {
            var result = get(actor, task, query(true, page), 200);
            assertThat(result.path("total").asInt()).isEqualTo(65); assertThat(result.path("page").asInt()).isEqualTo(page);
            assertThat(result.path("records")).hasSize(page == 4 ? 5 : 20);
            result.path("records").forEach(row -> { assertThat(ids.add(row.path("id").asLong())).isTrue(); assertThat(row.path("canApprove").asBoolean()).isTrue(); });
        }
        assertThat(ids).hasSize(65).doesNotContain(actor.id(), applicant.id());
        assertThat(get(actor, task, query(true, 5), 200).path("records")).isEmpty();
    }
    @Test void copyCandidatesIncludeReadOnlyUsersAndTheApplicantWithoutGrantingApprovalAuthority() throws Exception {
        var first = get(actor, task, query(false, 1), 200);
        assertThat(first.path("total").asInt()).isEqualTo(126);
        assertThat(first.path("records").get(0).path("id").asLong()).isEqualTo(applicant.id());
        assertThat(first.path("records").get(0).path("canApprove").asBoolean()).isFalse();
        assertThat(first.findValues("password")).isEmpty(); assertThat(first.findValues("email")).isEmpty();
        assertThat(first.findValues("avatar")).isEmpty(); assertThat(first.findValues("role")).isEmpty();
        var legacy = AgentHttpTestFixture.client().send(http.request("GET", "/api/approval-tasks/" + task + "/participant-candidates?keyword=" + marker, actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(legacy.statusCode()).isEqualTo(200); assertThat(json.readTree(legacy.body()).path("data")).hasSize(50);
    }
    @Test void searchesBothDisplayAndLoginNamesAndRejectsInvalidBounds() throws Exception {
        var exact = get(actor, task, "keyword=" + marker + "-approver-065&approversOnly=true", 200);
        assertThat(exact.path("total").asInt()).isEqualTo(1); assertThat(exact.path("records").get(0).path("name").asText()).contains("approver 65");
        for (String invalid : List.of("page=0", "page=10001", "size=0", "size=51", "keyword=" + "x".repeat(65), "approversOnly=invalid")) get(actor, task, invalid, 400);
        get(actor, 0, "", 400); get(actor, Long.MAX_VALUE, "", 404);
        assertThat(get(actor, task, "keyword=%27%20OR%201%3D1%20--", 200).path("total").asInt()).isZero();
    }
    @Test void checksAuthenticationAssignmentTenantAndLivePermissionWithTheSameCookie() throws Exception {
        get(null, task, "", 401); get(applicant, task, "", 403);
        get(http.createAndLogin(tenant, List.of()), task, "", 403);
        long other = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic other tenant') RETURNING id", Long.class, "PART_" + UUID.randomUUID().toString().substring(0, 16));
        get(http.createAndLogin(other, List.of("approval:act")), task, "", 404);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'", tenant, actor.role());
        get(actor, task, "", 403);
    }
    @Test void refusesClosedWrongBusinessAndBrokenCurrentInstanceAssociations() throws Exception {
        jdbc.update("UPDATE workflow_task SET status='CANCELLED' WHERE id=?", task); get(actor, task, "", 409);
        jdbc.update("UPDATE workflow_task SET status='PENDING',business_type='GENERIC_APPROVAL' WHERE id=?", task); get(actor, task, "", 404);
        jdbc.update("UPDATE workflow_task SET business_type='LEAVE_APPLICATION' WHERE id=?", task);
        jdbc.update("UPDATE leave_application SET workflow_instance_id=NULL WHERE id=?", application); get(actor, task, "", 404);
        jdbc.update("UPDATE leave_application SET workflow_instance_id=? WHERE id=?", instance, application);
        jdbc.update("UPDATE workflow_instance SET status='COMPLETED' WHERE id=?", instance); get(actor, task, "", 409);
    }
    @Test void enforcesTheGatewaysExpectedTenantAgainInsideTheDomain() {
        assertThatThrownBy(() -> service.participantCandidates(actor.id(), tenant + 10000, task, new BusinessCandidateQuery(marker, 1, 20), true))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus().value()).isEqualTo(403));
    }
    @Test void matchesAssignedRolesLegacyFallbackAndSuperAdminWhileExcludingDisabledAndForeignUsers() throws Exception {
        long legacy = candidate(tenant, "legacy", approverRole, 1);
        long superAdmin = candidate(tenant, "super", "SUPER_ADMIN", 1);
        long overridden = candidate(tenant, "overridden", "SUPER_ADMIN", 1);
        jdbc.update("INSERT INTO user_role(tenant_id,user_id,role_code) VALUES (?,?,?)", tenant, overridden, readerRole);
        long assigned = candidate(tenant, "assigned", readerRole, 1);
        jdbc.update("INSERT INTO user_role(tenant_id,user_id,role_code) VALUES (?,?,?)", tenant, assigned, approverRole);
        candidate(tenant, "disabled", approverRole, 0);
        long foreign = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Synthetic foreign participant') RETURNING id", Long.class, "PART_" + UUID.randomUUID().toString().substring(0, 16));
        candidate(foreign, "foreign", approverRole, 1);
        var result = get(actor, task, "keyword=" + marker + "special&approversOnly=true&size=50", 200);
        assertThat(result.path("total").asInt()).isEqualTo(3);
        var ids = new java.util.ArrayList<Long>(); result.path("records").forEach(row -> ids.add(row.path("id").asLong()));
        assertThat(ids).containsExactly(legacy, superAdmin, assigned);
    }
    private long candidate(long owner, String label, String role, int status) {
        return jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,display_name,password,role,status) VALUES (?,?,?,'synthetic-no-login',?,?) RETURNING id", Long.class,
                owner, marker + "special-" + label, marker + "special " + label, role, status);
    }
    @Test void rechecksCandidatePermissionsBeforeARealTransferAndDoesNotWriteForARevokedTarget() throws Exception {
        long target = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, marker + "-approver-065");
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=?", tenant, approverRole);
        assertThat(get(actor, task, query(true, 1), 200).path("total").asInt()).isZero();
        var response = AgentHttpTestFixture.client().send(http.request("POST", "/api/approval-tasks/" + task + "/transfer", actor, null,
                json.createObjectNode().put("targetUserId", target).put("version", 0).put("reason", "Synthetic transfer").toString()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?", Long.class, task)).isEqualTo(actor.id());
        assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?", Integer.class, task)).isZero();
    }
    @Test void executesTheFourthPageThroughTheRealWorkerAndGatewayWithoutAnyBusinessWrite() throws Exception {
        var frozen = queue(json.createObjectNode().put("taskId", task).put("keyword", marker).put("page", 4).put("size", 20).put("approversOnly", true));
        assertThat(run(frozen)).isEqualTo("SUCCEEDED");
        var result = json.readTree(jdbc.queryForObject("SELECT result::text FROM agent_task_step WHERE task_id=?", String.class, frozen.id()));
        assertThat(result.path("total").asInt()).isEqualTo(65); assertThat(result.path("page").asInt()).isEqualTo(4); assertThat(result.path("items")).hasSize(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked AND decision='ALLOW' AND outcome='SUCCEEDED'", Integer.class, frozen.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE instance_id=?", Integer.class, instance)).isZero();
        assertThat(runtime.isWriteToolsEnabled()).isFalse();
    }
    @Test void rejectsInvalidArgumentsAndOldFrozenContractsBeforeCallingAHandler() {
        for (var args : List.of(json.createObjectNode().put("taskId", task).put("size", 51), json.createObjectNode().put("taskId", task).put("tenantId", tenant))) {
            var frozen = queue(args); assertThat(run(frozen)).isEqualTo("FAILED"); assertNoHandler(frozen);
        }
        var old = queue(json.createObjectNode().put("taskId", task));
        var plan = json.createObjectNode();
        try { plan = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(jdbc.queryForObject("SELECT plan::text FROM agent_task WHERE id=?", String.class, old.id())); }
        catch (Exception invalid) { throw new AssertionError(invalid); }
        ((com.fasterxml.jackson.databind.node.ObjectNode) plan.path("steps").get(0)).put("schemaHash", "sha256:c1f565d9ed17668eb3c885274fcebdafdead56a51e4b560f329f6668eeca2dc3");
        jdbc.update("UPDATE agent_task SET plan=?::jsonb,plan_hash=? WHERE id=?", plan.toString(), hashing.hash(plan), old.id());
        jdbc.update("UPDATE agent_task_step SET schema_hash='sha256:c1f565d9ed17668eb3c885274fcebdafdead56a51e4b560f329f6668eeca2dc3' WHERE task_id=?", old.id());
        assertThat(run(old)).isEqualTo("FAILED"); assertNoHandler(old);
    }
    private FrozenReadTaskFixture.Task queue(JsonNode args) {
        return new FrozenReadTaskFixture(jdbc, json, hashing, catalog).create(tenant, actor.id(), "approval-list", "approval.task.participants.query", args, FrozenReadTaskFixture.State.QUEUED);
    }
    private String run(FrozenReadTaskFixture.Task frozen) {
        worker.poll(); await().atMost(Duration.ofSeconds(25)).until(() -> { worker.poll(); return !List.of("QUEUED", "RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, frozen.id())); });
        return jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, frozen.id());
    }
    private void assertNoHandler(FrozenReadTaskFixture.Task frozen) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Integer.class, frozen.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT result IS NULL FROM agent_task_step WHERE task_id=?", Boolean.class, frozen.id())).isTrue();
    }
}
