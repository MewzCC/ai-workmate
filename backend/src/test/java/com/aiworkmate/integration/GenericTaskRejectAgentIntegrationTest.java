package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Authenticated L2 rejection through real confirmation, Worker, Gateway and domain transactions. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenericTaskRejectAgentIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    private static final String CODE="approval.genericTask.reject";
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired javax.sql.DataSource datasource;
    @SpyBean AgentTaskWorker worker;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor reviewer,owner;
    private GenericTaskReadFixture.Submission submission;
    private long tenant;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        AgentBrowserFixture.registerLocal(p,ENVIRONMENT,AgentBrowserFixture.reserveUiPort(),SECRET);
        p.add("agent.enabled",()->"true");p.add("agent.execution-enabled",()->"true");p.add("agent.write-tools-enabled",()->"true");
        p.add("agent.worker.poll-delay-ms",()->"3600000");p.add("agent.worker.heartbeat-delay-ms",()->"3600000");
    }
    @BeforeEach void setup() throws Exception {
        org.mockito.Mockito.doNothing().when(worker).wakeAfterCommit(org.mockito.ArgumentMatchers.any(com.aiworkmate.agent.task.AgentTaskQueuedEvent.class));
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        reviewer=http.createAndLogin(tenant,List.of("route:todo","todo:read","approval:act","agent:tool:"+CODE));
        owner=http.createAndLogin(tenant,List.of("route:todo","todo:read"));
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?",tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?",CODE);
        submission=GenericTaskReadFixture.pending(jdbc,json,tenant,owner.id(),reviewer.id(),"Owned rejection",
                "{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}","{\"reason\":\"Frozen content\"}");
    }
    @AfterEach void cleanupFaults(){
        jdbc.execute("DROP TRIGGER IF EXISTS generic_reject_audit_fault ON business_audit_log");
        jdbc.execute("DROP TRIGGER IF EXISTS generic_reject_invocation_fault ON agent_tool_invocation");
        jdbc.execute("DROP FUNCTION IF EXISTS generic_reject_fault()");
    }
    @Test void rejectsAndEndsTheFlowWithoutANextNodeAndKeepsHistory() throws Exception {
        String plan=json.writeValueAsString(new com.aiworkmate.service.impl.GenericApprovalPlan(1,List.of(
                new com.aiworkmate.service.impl.GenericApprovalPlan.Step(0,"Frozen review node",reviewer.id(),0),
                new com.aiworkmate.service.impl.GenericApprovalPlan.Step(1,"Unreached next node",reviewer.id(),4))));
        jdbc.update("UPDATE approval_execution SET plan_json=? WHERE instance_id=?",plan,submission.instanceId());
        var task=plan(reviewer,0,"Missing evidence");run(task,reviewer,"SUCCEEDED");
        var result=data(response("GET",path(task),reviewer,null)).path("steps").get(0);
        assertThat(result.path("result").path("applicationStatus").asText()).isEqualTo("REJECTED");
        assertThat(result.path("result").path("version").asInt()).isOne();
        assertThat(result.path("resultSummary").asText()).contains("no subsequent task");
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_instance WHERE id=?",String.class,submission.instanceId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=?",Integer.class,submission.instanceId())).isOne();
        assertThat(jdbc.queryForObject("SELECT decision_comment FROM workflow_task WHERE id=?",String.class,submission.taskId())).isEqualTo("Missing evidence");
        assertDecisionCount(1);
    }
    @Test void missingConfirmationAndReusedConfirmationCannotExecuteAgain() throws Exception {
        var task=plan(reviewer,0,"Reason");assertThat(execute(task,reviewer,null).statusCode()).isEqualTo(409);noHandler(task);unchanged();
        String token=confirm(task,reviewer);assertThat(execute(task,reviewer,token).statusCode()).isEqualTo(202);terminal(task,"SUCCEEDED");
        assertThat(execute(task,reviewer,token).statusCode()).isNotEqualTo(202);assertDecisionCount(1);
    }
    @Test void whitespaceReasonFailsWithoutAnyDecision() throws Exception {
        var task=plan(reviewer,0,"   ");run(task,reviewer,"FAILED");unchanged();
    }
    @Test void wrongVersionWrongBusinessAndOtherAssigneesAreRejected() throws Exception {
        var stale=plan(reviewer,1,"Reason");run(stale,reviewer,"FAILED");unchanged();
        var other=http.createAndLogin(tenant,List.of("route:todo","approval:act","agent:tool:"+CODE));
        var unrelated=plan(other,0,"Reason");run(unrelated,other,"FAILED");unchanged();
        jdbc.update("UPDATE workflow_task SET business_type='LEAVE_APPLICATION' WHERE id=?",submission.taskId());
        var wrong=plan(reviewer,0,"Reason");run(wrong,reviewer,"FAILED");unchanged();
    }
    @Test void applicantCannotRejectTheirOwnApplication() throws Exception {
        for(String permission:List.of("approval:act","agent:tool:"+CODE))
            jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)",tenant,owner.role(),permission);
        jdbc.update("UPDATE workflow_task SET assignee_user_id=? WHERE id=?",owner.id(),submission.taskId());
        var task=plan(owner,0,"Reason");run(task,owner,"FAILED");unchanged();
    }
    @Test void aNewPlanCannotDecideAnAlreadyRejectedTaskAgain() throws Exception {
        var first=plan(reviewer,0,"Reason");run(first,reviewer,"SUCCEEDED");
        var repeated=plan(reviewer,1,"Another reason");run(repeated,reviewer,"FAILED");assertDecisionCount(1);
        assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?",Integer.class,submission.taskId())).isOne();
    }
    @Test void frozenArgumentTamperingIsRejectedBeforeAnyHandler() throws Exception {
        var task=plan(reviewer,0,"Reason");assertThat(execute(task,reviewer,confirm(task,reviewer)).statusCode()).isEqualTo(202);
        jdbc.update("UPDATE agent_task_step SET args=jsonb_set(args,'{comment}','\"Tampered reason\"'::jsonb) WHERE task_id=?",task.id());
        terminal(task,"FAILED");noHandler(task);unchanged();
    }
    @Test void liveRevocationBeforeEnqueueAndBeforeClaimFailsClosed() throws Exception {
        var task=plan(reviewer,0,"Reason");String token=confirm(task,reviewer);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?",tenant,reviewer.role(),"agent:tool:"+CODE);
        assertThat(execute(task,reviewer,token).statusCode()).isEqualTo(403);unchanged();
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)",tenant,reviewer.role(),"agent:tool:"+CODE);
        var queued=plan(reviewer,0,"Reason");assertThat(execute(queued,reviewer,confirm(queued,reviewer)).statusCode()).isEqualTo(202);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'",tenant,reviewer.role());
        terminal(queued,"FAILED");noHandler(queued);unchanged();
    }
    @Test void tenantChangesCannotExecuteThePreviouslyConfirmedTask() throws Exception {
        var task=plan(reviewer,0,"Reason");String token=confirm(task,reviewer);
        long foreign=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned isolation tenant') RETURNING id",Long.class,"RJ_"+UUID.randomUUID().toString().substring(0,16));
        jdbc.update("UPDATE app_user SET tenant_id=? WHERE id=?",foreign,reviewer.id());
        assertThat(execute(task,reviewer,token).statusCode()).isIn(403,404);noHandler(task);unchanged();
    }
    @Test void concurrentDecisionsAtTheSameTaskVersionHaveOneWinner() throws Exception {
        var a=plan(reviewer,0,"First reason");var b=plan(reviewer,0,"Second reason");
        String ta=confirm(a,reviewer),tb=confirm(b,reviewer);
        var ea=CompletableFuture.supplyAsync(()->executeUnchecked(a,ta));var eb=CompletableFuture.supplyAsync(()->executeUnchecked(b,tb));
        assertThat(ea.join()).isEqualTo(202);assertThat(eb.join()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(30)).until(()->{worker.poll();return isTerminal(a)&&isTerminal(b);});
        assertThat(List.of(status(a),status(b))).containsExactlyInAnyOrder("SUCCEEDED","FAILED");assertDecisionCount(1);
    }
    @Test void businessAuditFailureRollsBackTheDecisionAndNotifications() throws Exception {
        jdbc.execute("CREATE FUNCTION generic_reject_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_type='GENERIC_APPROVAL' AND NEW.action='REJECT' THEN RAISE EXCEPTION 'owned rejection fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER generic_reject_audit_fault BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION generic_reject_fault()");
        var task=plan(reviewer,0,"Reason");run(task,reviewer,"PARTIALLY_SUCCEEDED");unchanged();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE biz_type='generic-approval' AND biz_id=?",Integer.class,submission.applicationId())).isZero();
        worker.poll();assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,task.id())).isOne();
    }
    @Test void postInvocationAuditLossNeverReplaysAnUnknownWriteResult() throws Exception {
        invocationFault("UPDATE"," AND NEW.outcome='SUCCEEDED'");
        var task=plan(reviewer,0,"Reason");run(task,reviewer,"PARTIALLY_SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESULT_UNKNOWN");
        worker.poll();worker.poll();assertDecisionCount(1);
        assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,task.id())).isOne();
    }
    @Test void preInvocationAuditFailureAndTenantKillSwitchNeverReachTheHandler() throws Exception {
        invocationFault("INSERT","");var task=plan(reviewer,0,"Reason");run(task,reviewer,"FAILED");noHandler(task);unchanged();
        cleanupFaults();var killed=plan(reviewer,0,"Reason");assertThat(execute(killed,reviewer,confirm(killed,reviewer)).statusCode()).isEqualTo(202);
        jdbc.update("UPDATE agent_tenant_policy SET write_tools_enabled=false WHERE tenant_id=?",tenant);terminal(killed,"FAILED");noHandler(killed);unchanged();
    }
    @Test void migrationIsDisabledIdempotentAndPreservesOtherReleaseConfiguration(){
        var tool=catalog.find(CODE).orElseThrow();assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?",String.class,CODE)).isEqualTo(tool.schemaHash());
        var flyway=Flyway.configure().dataSource(datasource).schemas(ENVIRONMENT.schema()).defaultSchema(ENVIRONMENT.schema()).locations("classpath:db/migration").load();
        flyway.validate();assertThat(flyway.info().pending()).isEmpty();
        String previous=jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='approval.genericTask.approve'",String.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=false,write_tools_enabled=false WHERE tenant_id=?",tenant);
        jdbc.update("DELETE FROM agent_tool WHERE tenant_id IS NULL AND code=?",CODE);
        int replay=jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version::numeric>=202611091000",Integer.class);
        assertThat(replay).isPositive();assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version::numeric>=202611091000")).isEqualTo(replay);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(replay);flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?",Boolean.class,CODE)).isFalse();
        assertThat(jdbc.queryForObject("SELECT enabled OR write_tools_enabled FROM agent_tenant_policy WHERE tenant_id=?",Boolean.class,tenant)).isFalse();
        assertThat(jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='approval.genericTask.approve'",String.class)).isEqualTo(previous);
    }
    private FrozenWriteTaskFixture.Task plan(AgentHttpTestFixture.Actor actor,int version,String reason){return new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,actor.id(),"todo",CODE,json.createObjectNode().put("taskId",submission.taskId()).put("version",version).put("comment",reason));}
    private String confirm(FrozenWriteTaskFixture.Task t,AgentHttpTestFixture.Actor actor)throws Exception{var r=response("POST",path(t)+"/confirmation-token",actor,json.createObjectNode().put("planVersion",1).put("planHash",t.planHash()).toString());assertThat(r.statusCode()).isEqualTo(200);return data(r).path("confirmationToken").asText();}
    private HttpResponse<String> execute(FrozenWriteTaskFixture.Task t,AgentHttpTestFixture.Actor actor,String token)throws Exception{var b=json.createObjectNode().put("planVersion",1).put("planHash",t.planHash());if(token!=null)b.put("confirmationToken",token);return response("POST",path(t)+"/execute",actor,b.toString());}
    private int executeUnchecked(FrozenWriteTaskFixture.Task t,String token){try{return execute(t,reviewer,token).statusCode();}catch(Exception e){throw new IllegalStateException("Owned decision failed",e);}}
    private void run(FrozenWriteTaskFixture.Task t,AgentHttpTestFixture.Actor actor,String expected)throws Exception{assertThat(execute(t,actor,confirm(t,actor)).statusCode()).isEqualTo(202);terminal(t,expected);}
    private String path(FrozenWriteTaskFixture.Task t){return "/api/ai/tasks/"+t.number();}
    private HttpResponse<String> response(String method,String path,AgentHttpTestFixture.Actor actor,String body)throws Exception{return AgentHttpTestFixture.client().send(http.request(method,path,actor,path.endsWith("/execute")?UUID.randomUUID().toString():null,body).build(),HttpResponse.BodyHandlers.ofString());}
    private JsonNode data(HttpResponse<String> r)throws Exception{return json.readTree(r.body()).path("data");}
    private String status(FrozenWriteTaskFixture.Task t){return jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,t.id());}
    private boolean isTerminal(FrozenWriteTaskFixture.Task t){return !List.of("QUEUED","RUNNING").contains(status(t));}
    private void terminal(FrozenWriteTaskFixture.Task t,String expected){await().atMost(Duration.ofSeconds(25)).until(()->{worker.poll();return isTerminal(t);});assertThat(status(t)).isEqualTo(expected);}
    private void noHandler(FrozenWriteTaskFixture.Task t){assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Integer.class,t.id())).isZero();}
    private void unchanged(){assertThat(jdbc.queryForObject("SELECT status='PENDING' AND version=0 FROM workflow_task WHERE id=?",Boolean.class,submission.taskId())).isTrue();assertThat(jdbc.queryForObject("SELECT status='PENDING' AND version=0 FROM approval_application WHERE id=?",Boolean.class,submission.applicationId())).isTrue();assertDecisionCount(0);}
    private void assertDecisionCount(int count){assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE instance_id=? AND action='REJECT'",Integer.class,submission.instanceId())).isEqualTo(count);assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='REJECT' AND result='SUCCESS'",Integer.class,Long.toString(submission.applicationId()))).isEqualTo(count);}
    private void invocationFault(String operation,String suffix){jdbc.execute("CREATE FUNCTION generic_reject_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tool_code='approval.genericTask.reject'"+suffix+" THEN RAISE EXCEPTION 'owned invocation fault'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER generic_reject_invocation_fault BEFORE "+operation+" ON agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION generic_reject_fault()");}
}
