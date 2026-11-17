package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.service.impl.GenericApprovalPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

/** Real frozen L2 plans, confirmation cookies, worker gateway and domain transactions in owned infrastructure. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties="spring.main.allow-bean-definition-overriding=true")
@Import(ManualDecisionWorkerConfiguration.class)
class GenericTaskApproveAgentIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    private static final String CODE="approval.genericTask.approve";
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired AgentTaskWorker worker;
    @Autowired javax.sql.DataSource datasource;
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
        // Keep queued-before-claim mutations deterministic; polling, Gateway and domain execution remain real.
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        reviewer=http.createAndLogin(tenant,List.of("route:todo","todo:read","approval:act","agent:tool:"+CODE));
        owner=http.createAndLogin(tenant,List.of("route:todo","todo:read"));
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?",tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?",CODE);
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id='todo' AND tool_code=?",tenant,CODE);
        submission=GenericTaskReadFixture.pending(jdbc,json,tenant,owner.id(),reviewer.id(),"Owned approval",
                "{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}","{\"reason\":\"Owned frozen reason\"}");
    }
    @AfterEach void cleanupFaults(){
        jdbc.execute("DROP TRIGGER IF EXISTS generic_approve_audit_fault ON business_audit_log");jdbc.execute("DROP FUNCTION IF EXISTS generic_approve_audit_fault()");
        jdbc.execute("DROP TRIGGER IF EXISTS generic_approve_invocation_fault ON agent_tool_invocation");jdbc.execute("DROP FUNCTION IF EXISTS generic_approve_invocation_fault()");
        jdbc.execute("DROP TRIGGER IF EXISTS generic_approve_business_probe ON approval_application");jdbc.execute("DROP TRIGGER IF EXISTS generic_approve_audit_probe ON business_audit_log");
        jdbc.execute("DROP FUNCTION IF EXISTS generic_approve_probe_fn()");
    }
    @Test void confirmsAndApprovesTheFinalNodeWithARealVersionedReceiptAndAtomicAudit() throws Exception {
        var task=plan(reviewer,submission.taskId(),0);installTransactionProbe();
        String token=confirm(task,reviewer);assertThat(execute(task,reviewer,token).statusCode()).isEqualTo(202);terminal(task,"SUCCEEDED");
        var step=data(response("GET",path(task),reviewer,null)).path("steps").get(0);var result=step.path("result");
        assertThat(result.path("taskId").asLong()).isEqualTo(submission.taskId());assertThat(result.path("applicationId").asLong()).isEqualTo(submission.applicationId());
        assertThat(result.path("status").asText()).isEqualTo("APPROVED");assertThat(result.path("applicationStatus").asText()).isEqualTo("APPROVED");assertThat(result.path("version").asInt()).isEqualTo(1);
        assertThat(step.path("resultSummary").asText()).contains("has completed approval").doesNotContain("agent.genericTask.");
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_instance WHERE id=?",String.class,submission.instanceId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM generic_approve_tx_probe WHERE application_id=?",Integer.class,submission.applicationId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT transaction_id) FROM generic_approve_tx_probe WHERE application_id=?",Integer.class,submission.applicationId())).isEqualTo(1);
        assertExactlyOneDecision();
    }
    @Test void reportsHandoffAsPendingAndCreatesOnlyTheNextValidAssignment() throws Exception {
        var next=http.createAndLogin(tenant,List.of("route:todo","todo:read","approval:act"));nextNode(next.id());
        var task=plan(reviewer,submission.taskId(),0);assertThat(execute(task,reviewer,confirm(task,reviewer)).statusCode()).isEqualTo(202);terminal(task,"SUCCEEDED");
        var step=data(response("GET",path(task),reviewer,null)).path("steps").get(0);
        assertThat(step.path("result").path("applicationStatus").asText()).isEqualTo("PENDING");
        assertThat(step.path("resultSummary").asText()).contains("remains pending").doesNotContain("has completed approval");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE tenant_id=? AND instance_id=? AND assignee_user_id=? AND process_node_index=1 AND status='PENDING' AND version=0",Integer.class,tenant,submission.instanceId(),next.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?",String.class,submission.applicationId())).isEqualTo("PENDING");
        assertExactlyOneDecision();
    }
    @Test void rejectsMissingOrMismatchedConfirmationAndNeverEnqueues() throws Exception {
        var task=plan(reviewer,submission.taskId(),0);
        assertThat(execute(task,reviewer,null).statusCode()).isEqualTo(409);assertThat(execute(task,reviewer,"not-the-issued-token").statusCode()).isEqualTo(409);
        assertNoHandler(task);assertUnchanged();
    }
    @Test void unrelatedUsersAndTenantsCannotConfirmOrExecuteTheOwnersTask() throws Exception {
        var task=plan(reviewer,submission.taskId(),0);String token=confirm(task,reviewer);
        var other=http.createAndLogin(tenant,List.of("route:todo","approval:act","agent:tool:"+CODE));
        assertThat(response("POST",path(task)+"/confirmation-token",other,confirmationBody(task)).statusCode()).isEqualTo(404);
        assertThat(execute(task,other,token).statusCode()).isEqualTo(404);
        long foreign=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned foreign approval tenant') RETURNING id",Long.class,"GAP_"+UUID.randomUUID().toString().substring(0,16));
        var stranger=http.createAndLogin(foreign,List.of("route:todo","approval:act","agent:tool:"+CODE));
        assertThat(execute(task,stranger,token).statusCode()).isEqualTo(404);assertNoHandler(task);assertUnchanged();
    }
    @Test void revocationAfterConfirmationAndAfterQueueingBothPreventBusinessWrites() throws Exception {
        var before=plan(reviewer,submission.taskId(),0);String token=confirm(before,reviewer);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?",tenant,reviewer.role(),"agent:tool:"+CODE);
        assertThat(execute(before,reviewer,token).statusCode()).isEqualTo(403);assertNoHandler(before);
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)",tenant,reviewer.role(),"agent:tool:"+CODE);
        var queued=plan(reviewer,submission.taskId(),0);assertThat(execute(queued,reviewer,confirm(queued,reviewer)).statusCode()).isEqualTo(202);
        assertThat(status(queued)).isEqualTo("QUEUED");
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'",tenant,reviewer.role());
        terminal(queued,"FAILED");assertNoHandler(queued);assertUnchanged();
    }
    @Test void rejectsOtherAssigneesWrongVersionsAndWrongBusinessTypesInTheDomain() throws Exception {
        var other=http.createAndLogin(tenant,List.of("route:todo","approval:act","agent:tool:"+CODE));
        var unrelated=plan(other,submission.taskId(),0);assertThat(execute(unrelated,other,confirm(unrelated,other)).statusCode()).isEqualTo(202);terminal(unrelated,"FAILED");assertUnchanged();
        var stale=plan(reviewer,submission.taskId(),1);assertThat(execute(stale,reviewer,confirm(stale,reviewer)).statusCode()).isEqualTo(202);terminal(stale,"FAILED");assertUnchanged();
        long leave=LeaveReadFixture.pending(jdbc,tenant,owner.id(),reviewer.id());long leaveTask=jdbc.queryForObject("SELECT id FROM workflow_task WHERE business_type='LEAVE_APPLICATION' AND business_id=?",Long.class,leave);
        var wrong=plan(reviewer,leaveTask,0);assertThat(execute(wrong,reviewer,confirm(wrong,reviewer)).statusCode()).isEqualTo(202);terminal(wrong,"FAILED");assertUnchanged();
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_task WHERE id=?",String.class,leaveTask)).isEqualTo("PENDING");
    }
    @Test void refusesAnInvalidNextApproverWithoutConsumingTheCurrentNode() throws Exception {
        nextNode(owner.id()); // The next node cannot make the applicant approve their own request.
        var task=plan(reviewer,submission.taskId(),0);assertThat(execute(task,reviewer,confirm(task,reviewer)).statusCode()).isEqualTo(202);terminal(task,"FAILED");assertUnchanged();
    }
    @Test void anApplicantCannotApproveTheirOwnTaskEvenWithAllToolPermissions() throws Exception {
        for(String permission:List.of("approval:act","agent:tool:"+CODE))
            jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)",tenant,owner.role(),permission);
        jdbc.update("UPDATE workflow_task SET assignee_user_id=? WHERE id=?",owner.id(),submission.taskId());
        var task=plan(owner,submission.taskId(),0);assertThat(execute(task,owner,confirm(task,owner)).statusCode()).isEqualTo(202);terminal(task,"FAILED");assertUnchanged();
    }
    @Test void concurrentConfirmedPlansCanCommitOnlyOneDecisionAtTheOriginalTaskVersion() throws Exception {
        var first=plan(reviewer,submission.taskId(),0);var second=plan(reviewer,submission.taskId(),0);
        String firstToken=confirm(first,reviewer),secondToken=confirm(second,reviewer);
        var a=CompletableFuture.supplyAsync(()->executeUnchecked(first,firstToken));var b=CompletableFuture.supplyAsync(()->executeUnchecked(second,secondToken));
        assertThat(a.join()).isEqualTo(202);assertThat(b.join()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(30)).until(()->{worker.poll();return isTerminal(first)&&isTerminal(second);});
        assertThat(List.of(status(first),status(second))).containsExactlyInAnyOrder("SUCCEEDED","FAILED");assertExactlyOneDecision();
        assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?",Integer.class,submission.taskId())).isEqualTo(1);
    }
    @Test void businessAuditFailureRollsBackStateCursorAndNotificationsAndDoesNotAutoRetry() throws Exception {
        var next=http.createAndLogin(tenant,List.of("route:todo","todo:read","approval:act"));nextNode(next.id());
        jdbc.execute("CREATE FUNCTION generic_approve_audit_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_type='GENERIC_APPROVAL' AND NEW.action='APPROVE' THEN RAISE EXCEPTION 'synthetic generic approval audit fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER generic_approve_audit_fault BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION generic_approve_audit_fault()");
        var task=plan(reviewer,submission.taskId(),0);assertThat(execute(task,reviewer,confirm(task,reviewer)).statusCode()).isEqualTo(202);terminal(task,"PARTIALLY_SUCCEEDED");assertUnchanged();
        assertThat(jdbc.queryForObject("SELECT current_node_index FROM approval_execution WHERE instance_id=?",Integer.class,submission.instanceId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=?",Integer.class,submission.instanceId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE biz_type='generic-approval' AND biz_id=?",Integer.class,submission.applicationId())).isZero();
        worker.poll();assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,task.id())).isEqualTo(1);
    }
    @Test void aLostPostInvocationAuditProducesAnUnknownResultAndNeverRepeatsTheCommittedApproval() throws Exception {
        jdbc.execute("CREATE FUNCTION generic_approve_invocation_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tool_code='approval.genericTask.approve' AND NEW.outcome='SUCCEEDED' THEN RAISE EXCEPTION 'synthetic post invocation fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER generic_approve_invocation_fault BEFORE UPDATE ON agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION generic_approve_invocation_fault()");
        var task=plan(reviewer,submission.taskId(),0);assertThat(execute(task,reviewer,confirm(task,reviewer)).statusCode()).isEqualTo(202);terminal(task,"PARTIALLY_SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESULT_UNKNOWN");
        worker.poll();worker.poll();assertExactlyOneDecision();assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,task.id())).isEqualTo(1);
    }
    @Test void unavailablePreInvocationAuditFailsClosedBeforeAnyBusinessHandler() throws Exception {
        jdbc.execute("CREATE FUNCTION generic_approve_invocation_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tool_code='approval.genericTask.approve' THEN RAISE EXCEPTION 'synthetic pre invocation fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER generic_approve_invocation_fault BEFORE INSERT ON agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION generic_approve_invocation_fault()");
        var task=plan(reviewer,submission.taskId(),0);assertThat(execute(task,reviewer,confirm(task,reviewer)).statusCode()).isEqualTo(202);terminal(task,"FAILED");assertNoHandler(task);assertUnchanged();
    }
    @Test void alteredFrozenArgumentsAreRejectedBeforeTheHandler() throws Exception {
        var task=plan(reviewer,submission.taskId(),0);assertThat(execute(task,reviewer,confirm(task,reviewer)).statusCode()).isEqualTo(202);
        assertThat(status(task)).isEqualTo("QUEUED");
        jdbc.update("UPDATE agent_task_step SET args=jsonb_set(args,'{version}','1'::jsonb) WHERE task_id=?",task.id());terminal(task,"FAILED");assertNoHandler(task);assertUnchanged();
    }
    @Test void tenantWriteKillSwitchRejectsQueuedWorkBeforeTheHandler() throws Exception {
        var task=plan(reviewer,submission.taskId(),0);assertThat(execute(task,reviewer,confirm(task,reviewer)).statusCode()).isEqualTo(202);
        assertThat(status(task)).isEqualTo("QUEUED");
        jdbc.update("UPDATE agent_tenant_policy SET write_tools_enabled=false WHERE tenant_id=?",tenant);terminal(task,"FAILED");assertNoHandler(task);assertUnchanged();
    }
    @Test void migrationRegistersTheFrozenDisabledWriteAndPreservesOtherToolsAndReleaseSwitches() {
        var tool=catalog.find(CODE).orElseThrow();assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?",String.class,CODE)).isEqualTo(tool.schemaHash());
        var flyway=Flyway.configure().dataSource(datasource).schemas(ENVIRONMENT.schema()).defaultSchema(ENVIRONMENT.schema()).locations("classpath:db/migration").load();
        flyway.validate();assertThat(flyway.info().pending()).isEmpty();
        String previous=jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='approval.genericTask.detail.query'",String.class);
        jdbc.update("UPDATE agent_tool SET enabled=false WHERE tenant_id IS NULL AND code=?",CODE);jdbc.update("UPDATE agent_tenant_policy SET enabled=false,write_tools_enabled=false WHERE tenant_id=?",tenant);
        jdbc.update("DELETE FROM agent_tool WHERE tenant_id IS NULL AND code=?",CODE);
        int replay=jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version::numeric>=202611041530",Integer.class);
        assertThat(replay).isPositive();assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version::numeric>=202611041530")).isEqualTo(replay);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(replay);flyway.validate();assertThat(flyway.info().pending()).isEmpty();assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?",Boolean.class,CODE)).isFalse();
        assertThat(jdbc.queryForObject("SELECT enabled OR write_tools_enabled FROM agent_tenant_policy WHERE tenant_id=?",Boolean.class,tenant)).isFalse();
        assertThat(jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='approval.genericTask.detail.query'",String.class)).isEqualTo(previous);
    }
    private FrozenWriteTaskFixture.Task plan(AgentHttpTestFixture.Actor actor,long taskId,int version){return new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,actor.id(),"todo",CODE,json.createObjectNode().put("taskId",taskId).put("version",version).put("comment","Controlled generic approval"));}
    private String confirm(FrozenWriteTaskFixture.Task task,AgentHttpTestFixture.Actor actor) throws Exception {var response=response("POST",path(task)+"/confirmation-token",actor,confirmationBody(task));assertThat(response.statusCode()).isEqualTo(200);return data(response).path("confirmationToken").asText();}
    private String confirmationBody(FrozenWriteTaskFixture.Task task){return json.createObjectNode().put("planVersion",1).put("planHash",task.planHash()).toString();}
    private HttpResponse<String> execute(FrozenWriteTaskFixture.Task task,AgentHttpTestFixture.Actor actor,String token) throws Exception {var body=json.createObjectNode().put("planVersion",1).put("planHash",task.planHash());if(token!=null)body.put("confirmationToken",token);return response("POST",path(task)+"/execute",actor,body.toString());}
    private int executeUnchecked(FrozenWriteTaskFixture.Task task,String token){try{return execute(task,reviewer,token).statusCode();}catch(Exception failed){throw new IllegalStateException("Owned concurrent execution failed",failed);}}
    private HttpResponse<String> response(String method,String path,AgentHttpTestFixture.Actor actor,String body) throws Exception {return AgentHttpTestFixture.client().send(http.request(method,path,actor,path.endsWith("/execute")?UUID.randomUUID().toString():null,body).build(),HttpResponse.BodyHandlers.ofString());}
    private JsonNode data(HttpResponse<String> response) throws Exception {return json.readTree(response.body()).path("data");}
    private String path(FrozenWriteTaskFixture.Task task){return "/api/ai/tasks/"+task.number();}
    private String status(FrozenWriteTaskFixture.Task task){return jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id());}
    private boolean isTerminal(FrozenWriteTaskFixture.Task task){return !List.of("QUEUED","RUNNING").contains(status(task));}
    private void terminal(FrozenWriteTaskFixture.Task task,String expected){await().atMost(Duration.ofSeconds(25)).until(()->{worker.poll();return isTerminal(task);});assertThat(status(task)).as("owned write error=%s",jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo(expected);}
    private void assertNoHandler(FrozenWriteTaskFixture.Task task){assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Integer.class,task.id())).isZero();}
    private void assertUnchanged(){assertThat(jdbc.queryForObject("SELECT status='PENDING' AND version=0 FROM workflow_task WHERE id=?",Boolean.class,submission.taskId())).isTrue();assertThat(jdbc.queryForObject("SELECT status='PENDING' AND version=0 FROM approval_application WHERE id=?",Boolean.class,submission.applicationId())).isTrue();assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE instance_id=? AND action='APPROVE'",Integer.class,submission.instanceId())).isZero();}
    private void assertExactlyOneDecision(){assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE instance_id=? AND action='APPROVE'",Integer.class,submission.instanceId())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE tenant_id=? AND actor_user_id=? AND resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='APPROVE' AND result='SUCCESS'",Integer.class,tenant,reviewer.id(),Long.toString(submission.applicationId()))).isEqualTo(1);}
    private void nextNode(long next) throws Exception {String plan=json.writeValueAsString(new GenericApprovalPlan(1,List.of(new GenericApprovalPlan.Step(0,"Frozen review node",reviewer.id(),0),new GenericApprovalPlan.Step(1,"Next frozen node",next,4))));jdbc.update("UPDATE approval_execution SET plan_json=? WHERE instance_id=?",plan,submission.instanceId());}
    private void installTransactionProbe(){
        jdbc.execute("CREATE TABLE IF NOT EXISTS generic_approve_tx_probe(application_id bigint,kind text,transaction_id bigint)");jdbc.execute("TRUNCATE generic_approve_tx_probe");
        jdbc.execute("CREATE FUNCTION generic_approve_probe_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_TABLE_NAME='approval_application' THEN INSERT INTO generic_approve_tx_probe VALUES (NEW.id,'BUSINESS',txid_current()); ELSIF NEW.resource_type='GENERIC_APPROVAL' AND NEW.action='APPROVE' THEN INSERT INTO generic_approve_tx_probe VALUES (NEW.resource_id::bigint,'AUDIT',txid_current()); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER generic_approve_business_probe AFTER UPDATE ON approval_application FOR EACH ROW EXECUTE FUNCTION generic_approve_probe_fn()");
        jdbc.execute("CREATE TRIGGER generic_approve_audit_probe AFTER INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION generic_approve_probe_fn()");
    }
}
