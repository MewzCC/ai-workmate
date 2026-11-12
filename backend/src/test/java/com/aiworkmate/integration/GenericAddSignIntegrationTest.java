package com.aiworkmate.integration;

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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenericAddSignIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment(){return ENV;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner,reviewer,first,second;
    private GenericTaskReadFixture.Submission submission;
    private long tenant;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){AgentBrowserFixture.registerLocal(p,ENV,AgentBrowserFixture.reserveUiPort(),SECRET);}
    @BeforeEach void setup()throws Exception{
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        owner=http.createAndLogin(tenant,List.of("todo:read","route:approval-start","approval:withdraw"));
        var permissions=List.of("todo:read","approval:act","route:todo");
        reviewer=http.createAndLogin(tenant,permissions);first=http.createAndLogin(tenant,permissions);second=http.createAndLogin(tenant,permissions);
        submission=GenericTaskReadFixture.pending(jdbc,json,tenant,owner.id(),reviewer.id(),"Frozen add-sign request",
                "{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}","{\"reason\":\"PRIVATE FROZEN DATA\"}");
    }
    @ParameterizedTest @ValueSource(strings={"PRE","POST"})
    void completesTheDeclaredOrderWithoutEditingTheFrozenPlan(String mode)throws Exception{
        String frozen=jdbc.queryForObject("SELECT plan_json FROM approval_execution WHERE instance_id=?",String.class,submission.instanceId());
        var receipt=add(mode,first.id(),0);long added=receipt.path("addedTaskId").asLong();
        assertThat(added).isNotEqualTo(submission.taskId());assertThat(receipt.path("version").asInt()).isOne();
        assertThat(receipt.path("applicationStatus").asText()).isEqualTo("PENDING");assertPending(1);
        if(mode.equals("PRE")){
            assertThat(receipt.path("status").asText()).isEqualTo("WAITING");
            decide(submission.taskId(),reviewer,1,false,409);
            assertThat(decide(added,first,0,false,200).path("applicationStatus").asText()).isEqualTo("PENDING");assertPending(1);
            assertThat(decide(submission.taskId(),reviewer,2,false,200).path("applicationStatus").asText()).isEqualTo("APPROVED");
        }else{
            assertThat(receipt.path("addedTaskStatus").asText()).isEqualTo("WAITING");
            decide(added,first,0,false,409);
            assertThat(decide(submission.taskId(),reviewer,1,false,200).path("applicationStatus").asText()).isEqualTo("PENDING");assertPending(1);
            assertThat(decide(added,first,1,false,200).path("applicationStatus").asText()).isEqualTo("APPROVED");
        }
        assertPending(0);assertThat(jdbc.queryForObject("SELECT plan_json FROM approval_execution WHERE instance_id=?",String.class,submission.instanceId())).isEqualTo(frozen);
    }
    @Test void multiplePostSignersCannotSkipTheQueue()throws Exception{
        long a=add("POST",first.id(),0).path("addedTaskId").asLong(),b=add("POST",second.id(),1).path("addedTaskId").asLong();
        decide(submission.taskId(),reviewer,2,false,200);assertPending(1);decide(b,second,0,false,409);
        assertThat(decide(a,first,1,false,200).path("applicationStatus").asText()).isEqualTo("PENDING");assertPending(1);
        assertThat(decide(b,second,1,false,200).path("applicationStatus").asText()).isEqualTo("APPROVED");assertPending(0);
    }
    @Test void rejectionCancelsSuspendedAndScheduledTasks()throws Exception{
        add("POST",first.id(),0);long pre=add("PRE",second.id(),1).path("addedTaskId").asLong();
        assertThat(decide(pre,second,0,true,200).path("applicationStatus").asText()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=? AND status IN ('PENDING','WAITING')",Integer.class,submission.instanceId())).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"PRE","POST"})
    void withdrawalCancelsTheActiveAndAllWaitingTasks(String mode)throws Exception{
        add(mode,first.id(),0);
        request("POST","/api/approval-applications/"+submission.applicationId()+"/withdraw",owner,"{\"version\":1}",200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=? AND status IN ('PENDING','WAITING')",Integer.class,submission.instanceId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE instance_id=? AND action LIKE 'ADD_SIGN_%'",Integer.class,submission.instanceId())).isOne();
    }
    private JsonNode add(String mode,long target,int version)throws Exception{return request("POST",path(submission.taskId())+"/add-sign",reviewer,
            json.createObjectNode().put("targetUserId",target).put("version",version).put("mode",mode).put("reason","Explicit additional review").toString(),200);}
    @Test void rejectsDuplicateParticipantsStaleVersionsAndNestedAdditions() throws Exception {
        for (long forbidden : List.of(owner.id(), reviewer.id()))
            request("POST", path(submission.taskId()) + "/add-sign", reviewer, addBody("POST", forbidden, 0), 400);
        request("POST", path(submission.taskId()) + "/add-sign", reviewer, addBody("POST", first.id(), 1), 409);
        long child = add("PRE", first.id(), 0).path("addedTaskId").asLong();
        request("POST", path(child) + "/add-sign", first, addBody("POST", second.id(), 0), 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE parent_task_id=?", Integer.class, submission.taskId())).isOne();
    }
    @Test void revokedQueuedReviewerBlocksActivationWithoutPartiallyApprovingTheRoot() throws Exception {
        add("POST", first.id(), 0);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'", tenant, first.role());
        decide(submission.taskId(), reviewer, 1, false, 409);
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_task WHERE id=?", String.class, submission.taskId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?", Integer.class, submission.taskId())).isOne();
        assertPending(1);
    }
    @Test void concurrentRequestsForTheSameVersionProduceOneAddition() throws Exception {
        var client = AgentHttpTestFixture.client();
        var a = client.sendAsync(http.request("POST", path(submission.taskId()) + "/add-sign", reviewer, null, addBody("POST", first.id(), 0)).build(), HttpResponse.BodyHandlers.ofString());
        var b = client.sendAsync(http.request("POST", path(submission.taskId()) + "/add-sign", reviewer, null, addBody("POST", second.id(), 0)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(a.get().statusCode(), b.get().statusCode())).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE parent_task_id=?", Integer.class, submission.taskId())).isOne();
        assertPending(1);
    }
    @Test void inactiveReviewerAndAnUnassignedActorCannotCreateAdditionalTasks() throws Exception {
        jdbc.update("UPDATE app_user SET status=0 WHERE id=?", first.id());
        request("POST", path(submission.taskId()) + "/add-sign", reviewer, addBody("PRE", first.id(), 0), 409);
        request("POST", path(submission.taskId()) + "/add-sign", second, addBody("PRE", reviewer.id(), 0), 403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE parent_task_id=?", Integer.class, submission.taskId())).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"withdraw", "approve"})
    void concurrentAddSignAndTerminalMutationHaveOneAggregateWinner(String action) throws Exception {
        var client = AgentHttpTestFixture.client();
        var added = client.sendAsync(http.request("POST", path(submission.taskId()) + "/add-sign", reviewer, null, addBody("POST", first.id(), 0)).build(), HttpResponse.BodyHandlers.ofString());
        String targetPath = action.equals("withdraw") ? "/api/approval-applications/" + submission.applicationId() + "/withdraw" : path(submission.taskId()) + "/approve";
        var terminalBody = json.createObjectNode().put("version", 0);
        if (!action.equals("withdraw")) terminalBody.put("comment", "Reviewed");
        var terminal = client.sendAsync(http.request("POST", targetPath, action.equals("withdraw") ? owner : reviewer, null,
                terminalBody.toString()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(added.get().statusCode(), terminal.get().statusCode())).containsExactlyInAnyOrder(200, 409);
        String status = jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, submission.applicationId());
        if (status.equals("PENDING")) {
            assertPending(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE parent_task_id=? AND status='WAITING'", Integer.class, submission.taskId())).isOne();
        } else {
            assertThat(status).isEqualTo(action.equals("withdraw") ? "WITHDRAWN" : "APPROVED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=? AND status IN ('PENDING','WAITING')", Integer.class, submission.instanceId())).isZero();
        }
    }
    private String addBody(String mode, long target, int version) {
        return json.createObjectNode().put("targetUserId", target).put("version", version).put("mode", mode).put("reason", "Explicit additional review").toString();
    }
    @Test void crossTenantRecipientAndActorCannotChangeTheApplication() throws Exception {
        long foreign = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned add-sign isolation') RETURNING id", Long.class, "N7_" + UUID.randomUUID().toString().substring(0, 16));
        var outsider = http.createAndLogin(foreign, List.of("todo:read", "approval:act", "route:todo"));
        request("POST", path(submission.taskId()) + "/add-sign", reviewer, addBody("PRE", outsider.id(), 0), 409);
        request("POST", path(submission.taskId()) + "/add-sign", outsider, addBody("PRE", first.id(), 0), 404);
        assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?", Integer.class, submission.taskId())).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"business_audit_log", "notification"})
    void auditAndNotificationFailuresRollBackTheEntireAddition(String table) throws Exception {
        jdbc.execute("CREATE FUNCTION generic_add_sign_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'owned add-sign fault'; END $$");
        jdbc.execute("CREATE TRIGGER generic_add_sign_fault BEFORE INSERT ON " + table + " FOR EACH ROW EXECUTE FUNCTION generic_add_sign_fault()");
        try {
            request("POST", path(submission.taskId()) + "/add-sign", reviewer, addBody("PRE", first.id(), 0), 500);
            assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?", Integer.class, submission.taskId())).isZero();
            assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?", Integer.class, submission.applicationId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE parent_task_id=?", Integer.class, submission.taskId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE instance_id=? AND action LIKE 'ADD_SIGN_%'", Integer.class, submission.instanceId())).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS generic_add_sign_fault ON " + table);
            jdbc.execute("DROP FUNCTION IF EXISTS generic_add_sign_fault()");
        }
    }
    @Test void childTransferPreservesDistinctReviewAndResumesTheRootAfterApproval() throws Exception {
        long child = add("PRE", first.id(), 0).path("addedTaskId").asLong();
        request("POST", path(child) + "/transfer", first, json.createObjectNode().put("targetUserId", reviewer.id()).put("version", 0).put("reason", "Must not bypass review").toString(), 403);
        request("POST", path(child) + "/transfer", first, json.createObjectNode().put("targetUserId", second.id()).put("version", 0).put("reason", "Qualified handoff").toString(), 200);
        decide(child, first, 1, false, 403);
        assertThat(decide(child, second, 1, false, 200).path("applicationStatus").asText()).isEqualTo("PENDING");
        assertPending(1);
        assertThat(decide(submission.taskId(), reviewer, 2, false, 200).path("applicationStatus").asText()).isEqualTo("APPROVED");
    }
    private JsonNode decide(long task,AgentHttpTestFixture.Actor actor,int version,boolean reject,int expected)throws Exception{return request("POST",path(task)+(reject?"/reject":"/approve"),actor,
            json.createObjectNode().put("version",version).put("comment",reject?"Reject with evidence":"Reviewed").toString(),expected);}
    private void assertPending(int count){assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=? AND status='PENDING'",Integer.class,submission.instanceId())).isEqualTo(count);}
    private String path(long task){return "/api/generic-approval-tasks/"+task;}
    private JsonNode request(String method,String path,AgentHttpTestFixture.Actor actor,String body,int status)throws Exception{
        var response=AgentHttpTestFixture.client().send(http.request(method,path,actor,null,body).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s",method,path).isEqualTo(status);return json.readTree(response.body()).path("data");
    }
}
