package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenericTaskCollaborationIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment(){return ENV;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor owner,reviewer,target,reader;
    private GenericTaskReadFixture.Submission submission;
    private long tenant;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        AgentBrowserFixture.registerLocal(p,ENV,AgentBrowserFixture.reserveUiPort(),SECRET);
    }
    @BeforeEach void setup() throws Exception {
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        owner=http.createAndLogin(tenant,List.of("todo:read"));
        reviewer=http.createAndLogin(tenant,List.of("todo:read","approval:act","route:todo"));
        target=http.createAndLogin(tenant,List.of("todo:read","approval:act","route:todo"));
        jdbc.update("UPDATE app_user SET display_name=? WHERE id=?","Target_"+target.id(),target.id());
        reader=http.createAndLogin(tenant,List.of("todo:read","route:todo"));
        submission=GenericTaskReadFixture.pending(jdbc,json,tenant,owner.id(),reviewer.id(),"Frozen request",
                "{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}","{\"reason\":\"Owned value\"}");
    }
    @Test void transferKeepsFrozenPlanButNewAssigneeCanActuallyApprove() throws Exception {
        String frozen=jdbc.queryForObject("SELECT plan_json::text FROM approval_execution WHERE instance_id=?",String.class,submission.instanceId());
        var receipt=post("transfer",reviewer,target.id(),0,200);
        assertThat(receipt.path("action").asText()).isEqualTo("TRANSFER");
        assertThat(receipt.path("originalAssigneeUserId").asLong()).isEqualTo(reviewer.id());
        assertThat(receipt.path("assigneeUserId").asLong()).isEqualTo(target.id());
        assertThat(receipt.path("version").asInt()).isOne();
        assertThat(jdbc.queryForObject("SELECT plan_json::text FROM approval_execution WHERE instance_id=?",String.class,submission.instanceId())).isEqualTo(frozen);
        post("transfer",reviewer,target.id(),0,403);
        var detail=request("GET",path(),target,null,200);assertThat(detail.path("canApprove").asBoolean()).isTrue();
        request("POST",path()+"/approve",target,"{\"version\":1}",200);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?",String.class,submission.applicationId())).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE task_id=? AND action='TRANSFER' AND original_assignee_user_id=? AND target_user_id=?",Integer.class,submission.taskId(),reviewer.id(),target.id())).isOne();
    }
    @Test void copyIsVersionedReadOnlyAndNeverCreatesAnApprovalTaskForRecipient() throws Exception {
        request("GET",path(),reader,null,403);
        var receipt=post("cc",reviewer,reader.id(),0,200);
        assertThat(receipt.path("action").asText()).isEqualTo("CC");assertThat(receipt.path("version").asInt()).isOne();
        var detail=request("GET",path(),reader,null,200);
        assertThat(detail.path("canApprove").asBoolean()).isFalse();assertThat(detail.path("canReject").asBoolean()).isFalse();
        request("POST",path()+"/approve",reader,"{\"version\":1}",403);
        post("cc",reviewer,reader.id(),0,409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=?",Integer.class,submission.instanceId())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE tenant_id=? AND user_id=? AND biz_type='generic-approval-task' AND biz_id=?",Integer.class,tenant,reader.id(),submission.taskId())).isOne();
    }
    @Test void candidatesAreRealPagedQualifiedAndForbiddenForOtherActors() throws Exception {
        String query="&keyword=Target_"+target.id();
        var page=request("GET",path()+"/participants?purpose=TRANSFER&page=1&size=1"+query,reviewer,null,200);
        assertThat(page.path("records")).hasSize(1);assertThat(page.path("total").asInt()).isOne();
        assertThat(page.path("records").get(0).path("id").asLong()).isEqualTo(target.id());
        assertThat(request("GET",path()+"/participants?purpose=TRANSFER&page=2&size=1"+query,reviewer,null,200).path("records")).isEmpty();
        request("GET",path()+"/participants?purpose=ARBITRARY",reviewer,null,400);
        request("GET",path()+"/participants?purpose=TRANSFER",reader,null,403);
        post("transfer",reviewer,owner.id(),0,403);post("transfer",reviewer,reader.id(),0,403);
        post("transfer",reviewer,reviewer.id(),0,400);
    }
    @AfterEach void clearFaults(){
        jdbc.execute("DROP TRIGGER IF EXISTS generic_collaboration_fault ON business_audit_log");
        jdbc.execute("DROP TRIGGER IF EXISTS generic_collaboration_fault ON notification");
        jdbc.execute("DROP FUNCTION IF EXISTS generic_collaboration_fault()");
    }
    @Test void liveRecipientRevocationAndCrossTenantRecipientsFailBeforeAnyMutation() throws Exception {
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'",tenant,target.role());
        post("transfer",reviewer,target.id(),0,403);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='todo:read'",tenant,reader.role());
        post("cc",reviewer,reader.id(),0,403);
        long foreign=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned foreign tenant') RETURNING id",Long.class,"N6_"+UUID.randomUUID().toString().substring(0,16));
        var outsider=http.createAndLogin(foreign,List.of("todo:read","approval:act"));
        post("transfer",reviewer,outsider.id(),0,403);post("cc",reviewer,outsider.id(),0,403);
        assertUnchanged();
    }
    @Test void concurrentTransferAndCopyHaveOnlyOneTransactionWinner() throws Exception {
        var transfer=java.util.concurrent.CompletableFuture.supplyAsync(()->status("transfer",target.id()));
        var copy=java.util.concurrent.CompletableFuture.supplyAsync(()->status("cc",reader.id()));
        int a=transfer.get(20,java.util.concurrent.TimeUnit.SECONDS),b=copy.get(20,java.util.concurrent.TimeUnit.SECONDS);
        assertThat(List.of(a,b).stream().filter(code->code==200).count()).isOne();
        assertThat(List.of(a,b)).allMatch(code->List.of(200,403,409).contains(code));
        assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?",Integer.class,submission.taskId())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE task_id=? AND action IN ('TRANSFER','CC')",Integer.class,submission.taskId())).isOne();
    }
    @Test void auditFailureRollsBackAssignmentVersionAndNotification() throws Exception {
        fault("business_audit_log");post("transfer",reviewer,target.id(),0,500);assertUnchanged();
    }
    @Test void notificationFailureRollsBackCopyGrantAndAllVersions() throws Exception {
        fault("notification");post("cc",reviewer,reader.id(),0,500);assertUnchanged();
        request("GET",path(),reader,null,403);
    }
    @Test void transferredTaskCanTransferAgainWithoutChangingItsFrozenOriginalNode() throws Exception {
        post("transfer",reviewer,target.id(),0,200);
        post("transfer",target,reviewer.id(),1,200);
        assertThat(request("GET",path(),reviewer,null,200).path("canApprove").asBoolean()).isTrue();
        request("POST",path()+"/approve",reviewer,"{\"version\":2}",200);
    }
    @Test void inconsistentTransferHistoryFailsClosedEvenWhenAssigneeMatchesFrozenOriginal()throws Exception{
        jdbc.update("INSERT INTO workflow_action_log(tenant_id,instance_id,task_id,actor_user_id,original_assignee_user_id,target_user_id,action,from_status,to_status,comment,trace_id) VALUES (?,?,?,?,?,?,'TRANSFER','PENDING','PENDING','Owned inconsistent history','owned-history')",
                tenant,submission.instanceId(),submission.taskId(),reviewer.id(),reviewer.id(),target.id());
        request("POST",path()+"/approve",reviewer,"{\"version\":0}",409);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?",String.class,submission.applicationId())).isEqualTo("PENDING");
    }
    @Test void inactiveTargetsAndClosedTasksCannotCollaborate()throws Exception{
        jdbc.update("UPDATE app_user SET status=0 WHERE id=?",target.id());post("transfer",reviewer,target.id(),0,403);assertUnchanged();
        request("POST",path()+"/approve",reviewer,"{\"version\":0}",200);
        post("cc",reviewer,reader.id(),1,409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE task_id=? AND action='CC'",Integer.class,submission.taskId())).isZero();
    }
    private int status(String action,long targetId){
        try{return AgentHttpTestFixture.client().send(http.request("POST",path()+"/"+action,reviewer,null,
                json.createObjectNode().put("targetUserId",targetId).put("version",0).put("reason","Concurrent explicit reason").toString()).build(),HttpResponse.BodyHandlers.ofString()).statusCode();}
        catch(Exception failure){throw new IllegalStateException("Owned request failed",failure);}
    }
    private void fault(String table){
        if(!List.of("business_audit_log","notification").contains(table))throw new IllegalArgumentException();
        jdbc.execute("CREATE FUNCTION generic_collaboration_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'owned collaboration fault'; END $$");
        jdbc.execute("CREATE TRIGGER generic_collaboration_fault BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION generic_collaboration_fault()");
    }
    private void assertUnchanged(){
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?",Long.class,submission.taskId())).isEqualTo(reviewer.id());
        assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?",Integer.class,submission.taskId())).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?",Integer.class,submission.applicationId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE task_id=? AND action IN ('TRANSFER','CC')",Integer.class,submission.taskId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE biz_type='generic-approval-task' AND biz_id=?",Integer.class,submission.taskId())).isZero();
    }
    private String path(){return "/api/generic-approval-tasks/"+submission.taskId();}
    private JsonNode post(String action,AgentHttpTestFixture.Actor actor,long targetId,int version,int status)throws Exception{
        return request("POST",path()+"/"+action,actor,json.createObjectNode().put("targetUserId",targetId).put("version",version).put("reason","Explicit collaboration reason").toString(),status);
    }
    private JsonNode request(String method,String path,AgentHttpTestFixture.Actor actor,String body,int status)throws Exception{
        var response=AgentHttpTestFixture.client().send(http.request(method,path,actor,null,body).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s",method,path).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
}
