package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.service.GenericApprovalTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Full manual submission/review transactions, using real HTTP and owned infrastructure only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenericApprovalTaskIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired GenericApprovalTaskService tasks;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner, first, second;
    private long tenant, form, process;
    private String key;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        AgentBrowserFixture.registerLocal(p, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        owner = http.createAndLogin(tenant, List.of("route:approval-start", "approval:create", "approval:submit", "approval:withdraw", "approval:reopen", "approval:remind", "approval:cancel"));
        first = http.createAndLogin(tenant, List.of("todo:read", "approval:act"));
        second = http.createAndLogin(tenant, List.of("todo:read", "approval:act"));
        key = "generic_" + UUID.randomUUID().toString().replace("-", "");
        form = jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,created_by) VALUES (?,?,'Frozen form',?,'ENABLED',?) RETURNING id", Long.class,
                tenant, key, "{\"fields\":[{\"name\":\"reason\",\"label\":\"Original reason\",\"type\":\"text\",\"required\":true},{\"name\":\"amount\",\"type\":\"money\"},{\"name\":\"bankAccount\",\"type\":\"text\"}]}", owner.id());
        process = jdbc.queryForObject("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status,created_by) VALUES (?,?,'Two stage',?,?,'ENABLED',?) RETURNING id", Long.class, tenant, key, form, nodes(), owner.id());
    }
    private String nodes() {
        var nodes = json.createArrayNode(); nodes.addObject().put("nodeType", "START").put("nodeName", "Start");
        nodes.addObject().put("nodeType", "APPROVAL").put("nodeName", "First review").put("approveType", "USER").put("targetKey", Long.toString(first.id()));
        nodes.addObject().put("nodeType", "APPROVAL").put("nodeName", "Second review").put("approveType", "USER").put("targetKey", Long.toString(second.id()));
        nodes.addObject().put("nodeType", "END").put("nodeName", "End"); return nodes.toString();
    }
    private long draft() throws Exception {
        var body = json.createObjectNode().put("formKey", key).put("processKey", key);
        body.putObject("formData").put("reason", "Original payload").put("amount", "12.50").put("bankAccount", "SYNTHETIC-PRIVATE-ACCOUNT");
        return ok("POST", "/api/approval-applications/drafts", owner, body.toString()).path("id").asLong();
    }
    private long submit(long id, int version) throws Exception {
        return ok("POST", "/api/approval-applications/" + id + "/submit", owner, "{\"version\":" + version + "}").path("taskId").asLong();
    }
    private JsonNode detail(AgentHttpTestFixture.Actor actor, long task) throws Exception { return ok("GET", path(task), actor, null); }
    private String path(long task) { return "/api/generic-approval-tasks/" + task; }
    private JsonNode ok(String method, String path, AgentHttpTestFixture.Actor actor, String body) throws Exception {
        var response = send(method, path, actor, body);
        assertThat(response.statusCode()).as("HTTP %s %s error=%s", method, path, json.readTree(response.body()).path("errorCode").asText()).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }
    private HttpResponse<String> send(String method, String path, AgentHttpTestFixture.Actor actor, String body) throws Exception {
        return AgentHttpTestFixture.client().send(http.request(method, path, actor, null, body).build(), HttpResponse.BodyHandlers.ofString());
    }
    private long pending(long id) { return jdbc.queryForObject("SELECT id FROM workflow_task WHERE tenant_id=? AND business_type='GENERIC_APPROVAL' AND business_id=? AND status='PENDING'", Long.class, tenant, id); }
    private long instance(long id) { return jdbc.queryForObject("SELECT workflow_instance_id FROM approval_application WHERE id=?", Long.class, id); }
    private void state(long id, String status, int version) {
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, id)).isEqualTo(status);
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?", Integer.class, id)).isEqualTo(version);
    }

    @Test void submitsToTheRealFirstApprovalThenAdvancesOneNodeAtATimeWithTransactionalHistory() throws Exception {
        long id = draft(), task = submit(id, 0), instance = instance(id);
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?", Long.class, task)).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=?", Integer.class, instance)).isOne();
        var view = detail(first, task); assertThat(view.path("canApprove").asBoolean()).isTrue();
        assertThat(view.toString()).contains("Original payload", "12.50").doesNotContain("SYNTHETIC-PRIVATE-ACCOUNT", "planJson", "dataJson");
        var advanced = ok("POST", path(task) + "/approve", first, "{\"version\":0,\"comment\":\"Reviewed\"}");
        assertThat(advanced.path("task").path("status").asText()).isEqualTo("APPROVED");
        assertThat(advanced.path("applicationStatus").asText()).isEqualTo("PENDING"); state(id, "PENDING", 2);
        long next = pending(id); assertThat(next).isNotEqualTo(task);
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?", Long.class, next)).isEqualTo(second.id());
        assertThat(jdbc.queryForObject("SELECT current_node_index FROM approval_execution WHERE instance_id=?", Integer.class, instance)).isOne();
        ok("POST", path(next) + "/approve", second, "{\"version\":0}"); state(id, "APPROVED", 3);
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_instance WHERE id=?", String.class, instance)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=? AND status='PENDING'", Integer.class, instance)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE instance_id=? AND action='APPROVE'", Integer.class, instance)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='APPROVE'", Integer.class, Long.toString(id))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE biz_type='generic-approval' AND biz_id=?", Integer.class, id)).isEqualTo(3);
        assertThat(send("POST", path(task) + "/approve", first, "{\"version\":0}").statusCode()).isEqualTo(409);
        assertThat(detail(first, task).path("canApprove").asBoolean()).isFalse();
    }

    @Test void freezesEveryAttemptAcrossConfigurationChangesRejectionEditingAndResubmission() throws Exception {
        long id = draft(), oldTask = submit(id, 0), oldInstance = instance(id);
        assertThat(send("POST", path(oldTask) + "/reject", first, "{\"version\":0,\"comment\":\" \"}").statusCode()).isEqualTo(400);
        ok("POST", path(oldTask) + "/reject", first, "{\"version\":0,\"comment\":\"Change reason\"}"); state(id, "REJECTED", 2);
        ok("POST", "/api/approval-applications/" + id + "/reopen", owner, "{\"version\":2}");
        jdbc.update("UPDATE approval_form SET schema_json=?,version=version+1 WHERE id=?", "{\"fields\":[{\"name\":\"reason\",\"label\":\"New label\",\"type\":\"text\"}]}", form);
        jdbc.update("UPDATE approval_process SET node_json=?,version=version+1 WHERE id=?", "[{\"nodeName\":\"New review\",\"approveType\":\"USER\",\"targetKey\":\"" + second.id() + "\"}]", process);
        ok("PUT", "/api/approval-applications/" + id + "/draft", owner, "{\"version\":3,\"formData\":{\"reason\":\"New payload\"}}");
        long nextTask = submit(id, 4); assertThat(instance(id)).isNotEqualTo(oldInstance);
        var historic = detail(first, oldTask); assertThat(historic.path("applicationStatus").asText()).isEqualTo("REJECTED");
        assertThat(historic.toString()).contains("Original payload", "Original reason", "Change reason").doesNotContain("New payload", "New label");
        assertThat(send("GET", path(nextTask), first, null).statusCode()).isEqualTo(403);
        assertThat(detail(second, nextTask).toString()).contains("New payload", "New label").doesNotContain("Original payload");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_execution WHERE application_id=?", Integer.class, id)).isEqualTo(2);
    }

    @Test void currentConfigurationChangesDoNotAlterTheSubmittedPayloadOrNextAssignee() throws Exception {
        long id = draft(), task = submit(id, 0);
        jdbc.update("UPDATE approval_form SET schema_json='{\"fields\":[]}',version=version+1 WHERE id=?", form);
        jdbc.update("UPDATE approval_process SET node_json='[]',version=version+1 WHERE id=?", process);
        assertThat(detail(first, task).path("fields")).hasSize(3);
        ok("POST", path(task) + "/approve", first, "{\"version\":0}");
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?", Long.class, pending(id))).isEqualTo(second.id());
    }

    @ParameterizedTest @ValueSource(strings={"ROLE","DEPARTMENT"})
    void resolvesOnlyEligibleSequentialGroupMembersAndFreezesTheirOrder(String kind) throws Exception {
        String target;
        if (kind.equals("ROLE")) {
            target = "SEQ_" + UUID.randomUUID().toString().substring(0,16);
            jdbc.update("INSERT INTO rbac_role(tenant_id,code,name,description,builtin) VALUES (?,?,'Group','Owned group',false)",tenant,target);
            for (var reviewer : List.of(first,second)) jdbc.update("INSERT INTO user_role(tenant_id,user_id,role_code) VALUES (?,?,?)",tenant,reviewer.id(),target);
        } else {
            target = Long.toString(jdbc.queryForObject("INSERT INTO department(tenant_id,code,name) VALUES (?,?,'Owned review group') RETURNING id",Long.class,tenant,"SEQ_"+UUID.randomUUID().toString().substring(0,16)));
            jdbc.update("UPDATE app_user SET department_id=? WHERE id IN (?,?)",Long.parseLong(target),first.id(),second.id());
        }
        jdbc.update("UPDATE approval_process SET node_json=? WHERE id=?", "[{\"nodeName\":\"Group review\",\"approveType\":\""+kind+"\",\"targetKey\":\""+target+"\",\"mode\":\"SEQUENTIAL\"}]",process);
        long id = draft(), task = submit(id,0);
        assertThat(json.readTree(jdbc.queryForObject("SELECT plan_json FROM approval_execution WHERE instance_id=?",String.class,instance(id))).path("steps")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?",Long.class,task)).isEqualTo(first.id());
        if (kind.equals("DEPARTMENT")) jdbc.update("UPDATE app_user SET department_id=NULL WHERE id=?",second.id());
        else jdbc.update("DELETE FROM user_role WHERE tenant_id=? AND user_id=? AND role_code=?",tenant,second.id(),target);
        ok("POST",path(task)+"/approve",first,"{\"version\":0}");
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?",Long.class,pending(id))).isEqualTo(second.id());
    }

    @Test void revokesDecisionPermissionWithTheSameCookieAndValidatesNextAssigneeBeforeWriting() throws Exception {
        long id = draft(), task = submit(id, 0);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'", tenant, first.role());
        assertThat(detail(first, task).path("canApprove").asBoolean()).isFalse();
        assertThat(send("POST", path(task) + "/approve", first, "{\"version\":0}").statusCode()).isEqualTo(403);
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,'approval:act')", tenant, first.role());
        jdbc.update("UPDATE app_user SET status=0 WHERE id=?", second.id());
        assertThat(send("POST", path(task) + "/approve", first, "{\"version\":0}").statusCode()).isEqualTo(409);
        state(id, "PENDING", 1); assertThat(pending(id)).isEqualTo(task);
        assertThat(jdbc.queryForObject("SELECT version FROM approval_execution WHERE instance_id=?", Integer.class, instance(id))).isZero();
    }

    @Test void protectsActorTenantBusinessKindAndFieldBounds() throws Exception {
        long id = draft(), task = submit(id, 0);
        var stranger = http.createAndLogin(tenant, List.of("todo:read", "approval:act"));
        long foreignTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned foreign tenant') RETURNING id", Long.class, "GEN_" + UUID.randomUUID().toString().substring(0, 16));
        var foreign = http.createAndLogin(foreignTenant, List.of("todo:read", "approval:act"));
        assertThat(send("GET", path(task), null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", path(task), stranger, null).statusCode()).isEqualTo(403);
        assertThat(send("GET", path(task), foreign, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", path(task) + "/approve", owner, "{\"version\":0}").statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/todos/" + task, first, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/api/approval-tasks/" + task + "/approve", first, "{\"version\":0}").statusCode()).isEqualTo(404);
        for (String query : List.of("?fieldPage=0", "?fieldPage=3")) assertThat(send("GET", path(task) + query, first, null).statusCode()).isEqualTo(400);
        assertThatThrownBy(() -> tasks.detail(first.id(), foreignTenant, task, 1)).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_FORBIDDEN");
    }

    @Test void anOutOfOrderPendingTaskCannotApproveOrReject() throws Exception {
        long id = draft(), task = submit(id, 0);
        jdbc.update("UPDATE workflow_task SET process_node_index=5 WHERE id=?", task);
        var view = detail(first, task); assertThat(view.path("canApprove").asBoolean()).isFalse(); assertThat(view.path("canReject").asBoolean()).isFalse();
        for (String action : List.of("approve", "reject")) assertThat(send("POST", path(task) + "/" + action, first, "{\"version\":0,\"comment\":\"Review\"}").statusCode()).isEqualTo(409);
        state(id, "PENDING", 1);
    }

    @Test void matchingFrozenRulesPreventSilentApprovalWhileAllowingAnExplicitRejection() throws Exception {
        String rule = "rule_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO approval_rule(tenant_id,rule_key,rule_name,rule_type,condition_json,action_json,status,created_by) VALUES (?,?,'Bounded rule','AMOUNT_THRESHOLD',?,?,'ENABLED',?)", tenant, rule,
                "{\"field\":\"amount\",\"op\":\"gte\",\"value\":0}", "{\"enabled\":true,\"appendNode\":\"FINANCE_REVIEW\"}", owner.id());
        try {
            long id = draft(), task = submit(id, 0);
            jdbc.update("UPDATE approval_rule SET status='DISABLED' WHERE tenant_id=? AND rule_key=?", tenant, rule);
            assertThat(detail(first, task).path("canApprove").asBoolean()).isFalse(); assertThat(detail(first, task).path("canReject").asBoolean()).isTrue();
            assertThat(send("POST", path(task) + "/approve", first, "{\"version\":0}").statusCode()).isEqualTo(409); state(id, "PENDING", 1);
            ok("POST", path(task) + "/reject", first, "{\"version\":0,\"comment\":\"Additional process required\"}"); state(id, "REJECTED", 2);
        } finally { jdbc.update("UPDATE approval_rule SET status='DISABLED' WHERE tenant_id=? AND rule_key=?", tenant, rule); }
    }

    @Test void unsupportedFlowCannotLeaveAPartiallySubmittedDraft() throws Exception {
        long id = draft(); jdbc.update("UPDATE approval_process SET node_json='[{\"nodeType\":\"CONDITION\",\"nodeName\":\"Unsupported\"}]' WHERE id=?", process);
        assertThat(send("POST", "/api/approval-applications/" + id + "/submit", owner, "{\"version\":0}").statusCode()).isEqualTo(409);
        state(id, "DRAFT", 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE business_type='GENERIC_APPROVAL' AND business_id=?", Integer.class, id)).isZero();
    }

    @Test void concurrentDraftSubmissionsCreateExactlyOneInstanceAndPendingTask() throws Exception {
        long id = draft(); CountDownLatch ready = new CountDownLatch(1);
        var a = CompletableFuture.supplyAsync(() -> concurrentDecision(ready, "/api/approval-applications/" + id + "/submit", owner, "{\"version\":0}"));
        var b = CompletableFuture.supplyAsync(() -> concurrentDecision(ready, "/api/approval-applications/" + id + "/submit", owner, "{\"version\":0}"));
        ready.countDown(); assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        state(id, "PENDING", 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_instance WHERE business_type='GENERIC_APPROVAL' AND business_id=?", Integer.class, id)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE business_type='GENERIC_APPROVAL' AND business_id=?", Integer.class, id)).isOne();
    }

    @Test void concurrentApprovalsCreateOnlyOneFollowingNode() throws Exception {
        long id = draft(), task = submit(id, 0); CountDownLatch ready = new CountDownLatch(1);
        var a = CompletableFuture.supplyAsync(() -> concurrentDecision(ready, path(task) + "/approve", first, "{\"version\":0}"));
        var b = CompletableFuture.supplyAsync(() -> concurrentDecision(ready, path(task) + "/approve", first, "{\"version\":0}"));
        ready.countDown(); assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        state(id, "PENDING", 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=? AND process_node_index=1", Integer.class, instance(id))).isOne();
    }

    @Test void approvalRacingWithdrawalHasOneConsistentWinnerAndNoOrphanedPendingTask() throws Exception {
        long id = draft(), task = submit(id, 0); CountDownLatch ready = new CountDownLatch(1);
        var a = CompletableFuture.supplyAsync(() -> concurrentDecision(ready, path(task) + "/approve", first, "{\"version\":0}"));
        var b = CompletableFuture.supplyAsync(() -> concurrentDecision(ready, "/api/approval-applications/" + id + "/withdraw", owner, "{\"version\":1}"));
        ready.countDown(); assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        String status = jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=? AND status='PENDING'", Integer.class, instance(id))).isEqualTo(status.equals("WITHDRAWN") ? 0 : 1);
    }
    private int concurrentDecision(CountDownLatch start, String path, AgentHttpTestFixture.Actor actor, String payload) {
        try { start.await(5, TimeUnit.SECONDS); return send("POST", path, actor, payload).statusCode(); }
        catch (Exception failure) { throw new IllegalStateException("Owned concurrent HTTP check failed", failure); }
    }

    @Test void auditFailureRollsBackApplicationTaskCursorNextTaskAndNotifications() throws Exception {
        long id = draft(), task = submit(id, 0), instance = instance(id);
        jdbc.execute("CREATE FUNCTION fail_generic_review_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_type='GENERIC_APPROVAL' AND NEW.action='APPROVE' THEN RAISE EXCEPTION 'owned audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_generic_review BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION fail_generic_review_audit()");
        try {
            assertThat(send("POST", path(task) + "/approve", first, "{\"version\":0}").statusCode()).isEqualTo(500);
            state(id, "PENDING", 1); assertThat(pending(id)).isEqualTo(task);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=?", Integer.class, instance)).isOne();
            assertThat(jdbc.queryForObject("SELECT version FROM approval_execution WHERE instance_id=?", Integer.class, instance)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE instance_id=? AND action='APPROVE'", Integer.class, instance)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE biz_type='generic-approval' AND biz_id=?", Integer.class, id)).isOne();
        } finally { jdbc.execute("DROP TRIGGER fail_generic_review ON business_audit_log"); jdbc.execute("DROP FUNCTION fail_generic_review_audit()"); }
    }

    @Test void legacyTasksKeepFrozenPayloadButNeverInventAnExecutablePlan() throws Exception {
        long id = draft(), task = submit(id, 0);
        jdbc.update("UPDATE approval_execution SET plan_json=NULL WHERE instance_id=?", instance(id));
        var value = detail(first, task); assertThat(value.path("canApprove").asBoolean()).isFalse(); assertThat(value.path("canReject").asBoolean()).isTrue();
        assertThat(send("POST", path(task) + "/approve", first, "{\"version\":0}").statusCode()).isEqualTo(409);
        jdbc.update("DELETE FROM approval_execution WHERE instance_id=?", instance(id));
        var missing = send("GET", path(task), first, null); assertThat(missing.statusCode()).isEqualTo(409);
        assertThat(missing.body()).doesNotContain("Original payload");
    }

    @Test void paginatesOnlyTheCurrentActorsGenericTasksAndRejectsInvalidQueries() throws Exception {
        for (int i = 0; i < 23; i++) { long id = draft(); submit(id, 0); }
        var one = ok("GET", "/api/generic-approval-tasks?page=1&size=20&status=PENDING", first, null);
        var two = ok("GET", "/api/generic-approval-tasks?page=2&size=20&status=PENDING", first, null);
        assertThat(one.path("total").asInt()).isEqualTo(23); assertThat(one.path("records")).hasSize(20); assertThat(two.path("records")).hasSize(3);
        var ids = new java.util.HashSet<Long>(); one.path("records").forEach(row -> ids.add(row.path("id").asLong()));
        two.path("records").forEach(row -> assertThat(ids.add(row.path("id").asLong())).isTrue());
        assertThat(ok("GET", "/api/generic-approval-tasks", second, null).path("total").asInt()).isZero();
        for (String query : List.of("?page=0", "?page=10001", "?size=51", "?status=WAITING", "?from=2026-11-04T12:00:00&to=2026-11-01T00:00:00"))
            assertThat(send("GET", "/api/generic-approval-tasks" + query, first, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/generic-approval-tasks", owner, null).statusCode()).isEqualTo(403);
    }
}
