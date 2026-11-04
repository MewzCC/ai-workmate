package com.aiworkmate.integration;

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
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LeaveTaskReadBoundaryIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner, reviewer, copied;
    private long tenant, definition, application, instance, task;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        AgentBrowserFixture.registerLocal(p, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        definition = jdbc.queryForObject("SELECT id FROM workflow_definition WHERE tenant_id=? AND code='LEAVE_SINGLE_APPROVAL' ORDER BY version DESC LIMIT 1", Long.class, tenant);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        owner = http.createAndLogin(tenant, List.of()); reviewer = http.createAndLogin(tenant, List.of("approval:act")); copied = http.createAndLogin(tenant, List.of());
        application = jdbc.queryForObject("INSERT INTO leave_application(tenant_id,applicant_user_id,approver_user_id,leave_type,start_date,start_period,end_date,end_period,duration_half_days,reason,status,version) VALUES (?,?,?,'PERSONAL',CURRENT_DATE+2,'AM',CURRENT_DATE+2,'PM',2,'Boundary synthetic leave reason','PENDING',1) RETURNING id", Long.class, tenant, owner.id(), reviewer.id());
        instance = instance(tenant, "LEAVE_APPLICATION", application, owner.id());
        task = task(instance, "LEAVE_APPLICATION", "PENDING", 0);
        jdbc.update("UPDATE leave_application SET workflow_instance_id=? WHERE id=?", instance, application);
        copy(instance, task);
    }
    private long instance(long scope, String type, long business, long applicant) {
        return jdbc.queryForObject("INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status) VALUES (?,?,?,?,?,'RUNNING') RETURNING id", Long.class, scope, definition, type, business, applicant);
    }
    private long task(long flow, String type, String status, int version) {
        return jdbc.queryForObject("INSERT INTO workflow_task(tenant_id,instance_id,business_type,business_id,assignee_user_id,status,version) VALUES (?,?,?,?,?,?,?) RETURNING id", Long.class, tenant, flow, type, application, reviewer.id(), status, version);
    }
    private void copy(long flow, long taskId) {
        jdbc.update("INSERT INTO workflow_action_log(tenant_id,instance_id,task_id,actor_user_id,action,to_status,target_user_id,trace_id) VALUES (?,?,?,?,'CC','PENDING',?,'boundary-fixture')", tenant, flow, taskId, owner.id(), copied.id());
    }
    private HttpResponse<String> read(AgentHttpTestFixture.Actor actor, long id, boolean timeline) throws Exception {
        String path = timeline ? "/api/approval-tasks/" + id + "/timeline" : "/api/todos/" + id;
        return AgentHttpTestFixture.client().send(http.request("GET", path, actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void rejectRead(AgentHttpTestFixture.Actor actor, long id, int status) throws Exception {
        for (boolean timeline : List.of(false, true)) {
            var response = read(actor, id, timeline);
            assertThat(response.statusCode()).as("task %s timeline %s", id, timeline).isEqualTo(status);
            assertThat(response.body()).doesNotContain("Boundary synthetic leave reason");
        }
    }
    @Test void retainsOwnerReviewerAndCopiedReadAccessWithoutGivingCopiedApprovalAuthority() throws Exception {
        for (var actor : List.of(owner, reviewer, copied)) {
            assertThat(read(actor, task, false).statusCode()).isEqualTo(200);
            assertThat(read(actor, task, true).statusCode()).isEqualTo(200);
        }
        assertThat(json.readTree(read(copied, task, false).body()).path("data").path("canApprove").asBoolean()).isFalse();
        var write = AgentHttpTestFixture.client().send(http.request("POST", "/api/approval-tasks/" + task + "/approve", copied, null, "{\"version\":0}").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(write.statusCode()).isEqualTo(403);
    }
    @Test void refusesAnotherBusinessWithTheSameApplicationNumberEvenForItsCopiedUserAndAnAuditor() throws Exception {
        long genericInstance = instance(tenant, "GENERIC_APPROVAL", application, copied.id());
        long genericTask = task(genericInstance, "GENERIC_APPROVAL", "PENDING", 0); copy(genericInstance, genericTask);
        for (var actor : List.of(owner, copied, http.createAndLogin(tenant, List.of("audit:read")))) rejectRead(actor, genericTask, 404);
    }
    @Test void refusesBrokenTaskInstanceAndApplicantAssociationsInsteadOfReadingAnUnrelatedLeave() throws Exception {
        jdbc.update("UPDATE workflow_instance SET business_type='GENERIC_APPROVAL' WHERE id=?", instance); rejectRead(owner, task, 404);
        jdbc.update("UPDATE workflow_instance SET business_type='LEAVE_APPLICATION',business_id=? WHERE id=?", application + 10000, instance); rejectRead(owner, task, 404);
        jdbc.update("UPDATE workflow_instance SET business_id=?,applicant_id=? WHERE id=?", application, copied.id(), instance); rejectRead(owner, task, 404);
        long foreign = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Boundary foreign') RETURNING id", Long.class, "READ_" + UUID.randomUUID().toString().replace("-", ""));
        jdbc.update("UPDATE workflow_instance SET applicant_id=?,tenant_id=? WHERE id=?", owner.id(), foreign, instance); rejectRead(owner, task, 404);
        jdbc.update("UPDATE workflow_instance SET tenant_id=? WHERE id=?", tenant, instance);
        var foreignActor = http.createAndLogin(foreign, List.of());
        jdbc.update("UPDATE workflow_task SET assignee_user_id=? WHERE id=?", foreignActor.id(), task); rejectRead(owner, task, 404);
    }
    @Test void refusesUnrelatedUsersForeignTenantsAndAnonymousReaders() throws Exception {
        rejectRead(http.createAndLogin(tenant, List.of()), task, 403);
        long foreign = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Boundary outsider') RETURNING id", Long.class, "READ_" + UUID.randomUUID().toString().replace("-", ""));
        rejectRead(http.createAndLogin(foreign, List.of("audit:read")), task, 404); rejectRead(null, task, 401);
    }
    @Test void refusesInvalidAndMissingTaskIdentifiers() throws Exception {
        rejectRead(owner, 0, 400); rejectRead(owner, -1, 400); rejectRead(owner, Long.MAX_VALUE, 404);
    }
    @Test void bindsHistoricalDetailToTheRequestedTaskAndKeepsClosedNodesReadOnly() throws Exception {
        long historical = task(instance, "LEAVE_APPLICATION", "CANCELLED", 7);
        var response = read(reviewer, historical, false); assertThat(response.statusCode()).isEqualTo(200);
        var data = json.readTree(response.body()).path("data");
        assertThat(data.path("taskId").asLong()).isEqualTo(historical); assertThat(data.path("taskVersion").asInt()).isEqualTo(7);
        assertThat(data.path("taskStatus").asText()).isEqualTo("CANCELLED"); assertThat(data.path("canApprove").asBoolean()).isFalse();
        var current = json.readTree(read(reviewer, task, false).body()).path("data");
        assertThat(current.path("taskId").asLong()).isEqualTo(task); assertThat(current.path("canApprove").asBoolean()).isTrue();
    }
    @Test void rechecksApprovalAuthorityWithTheSameCookieWithoutRemovingLegitimateReadAccess() throws Exception {
        assertThat(json.readTree(read(reviewer, task, false).body()).path("data").path("canApprove").asBoolean()).isTrue();
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'", tenant, reviewer.role());
        assertThat(json.readTree(read(reviewer, task, false).body()).path("data").path("canApprove").asBoolean()).isFalse();
        var write = AgentHttpTestFixture.client().send(http.request("POST", "/api/approval-tasks/" + task + "/approve", reviewer, null, "{\"version\":0}").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(write.statusCode()).isEqualTo(403);
    }

    @Test void preservesHistoricalAndCurrentAssigneeReadAccessWithoutTurningClosedNodesIntoActions() throws Exception {
        long historical = task(instance, "LEAVE_APPLICATION", "CANCELLED", 7);
        jdbc.update("UPDATE workflow_task SET assignee_user_id=? WHERE id=?", copied.id(), historical);
        jdbc.update("DELETE FROM workflow_action_log WHERE instance_id=? AND action='CC'", instance);
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,'approval:act')", tenant, copied.role());
        for (var actor : List.of(copied, reviewer)) {
            var response = read(actor, historical, false); assertThat(response.statusCode()).isEqualTo(200);
            var data = json.readTree(response.body()).path("data");
            assertThat(data.path("taskId").asLong()).isEqualTo(historical);
            assertThat(data.path("approverUserId").asLong()).isEqualTo(copied.id());
            assertThat(data.path("canApprove").asBoolean()).isFalse();
            assertThat(read(actor, historical, true).statusCode()).isEqualTo(200);
        }
    }
}
