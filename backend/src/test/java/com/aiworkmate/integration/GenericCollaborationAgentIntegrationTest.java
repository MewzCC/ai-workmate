package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenericCollaborationAgentIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment(){return ENV;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired javax.sql.DataSource datasource;
    @SpyBean AgentTaskWorker worker;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor reviewer,owner,target;
    private GenericTaskReadFixture.Submission submission;
    private ConfirmedDecisionFixture driver;
    private long tenant;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        AgentBrowserFixture.registerLocal(p,ENV,AgentBrowserFixture.reserveUiPort(),SECRET);
        p.add("agent.enabled",()->"true");p.add("agent.execution-enabled",()->"true");p.add("agent.write-tools-enabled",()->"true");
        p.add("agent.worker.poll-delay-ms",()->"3600000");p.add("agent.worker.heartbeat-delay-ms",()->"3600000");
    }
    @BeforeEach void setup()throws Exception{
        org.mockito.Mockito.doNothing().when(worker).wakeAfterCommit(org.mockito.ArgumentMatchers.any(com.aiworkmate.agent.task.AgentTaskQueuedEvent.class));
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        reviewer=http.createAndLogin(tenant,List.of("route:todo","todo:read","approval:act","agent:tool:approval.genericTask.participants.query","agent:tool:approval.genericTask.transfer","agent:tool:approval.genericTask.cc"));
        owner=http.createAndLogin(tenant,List.of("todo:read"));target=http.createAndLogin(tenant,List.of("todo:read","approval:act","route:todo"));
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?",tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code IN ('approval.genericTask.participants.query','approval.genericTask.transfer','approval.genericTask.cc')");
        submission=GenericTaskReadFixture.pending(jdbc,json,tenant,owner.id(),reviewer.id(),"Owned collaboration","{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}","{\"reason\":\"PRIVATE FORM DATA\"}");
        driver=new ConfirmedDecisionFixture(jdbc,json,http,worker,hashing,catalog,tenant,"todo");
    }
    @ParameterizedTest @ValueSource(strings={"transfer","cc"})
    void actualConfirmedWritesDeliverCorrectConcreteReceiptsAndNeverApprove(String action)throws Exception{
        var task=plan(action,submission.taskId(),target.id(),0);
        driver.run(task,reviewer,"SUCCEEDED");
        var step=driver.detail(task,reviewer).path("steps").get(0);var result=step.path("result");
        assertThat(result.path("action").asText()).isEqualTo(action.equals("cc")?"CC":"TRANSFER");
        assertThat(result.path("targetUserId").asLong()).isEqualTo(target.id());assertThat(result.path("version").asInt()).isOne();
        assertThat(step.path("resultSummary").asText()).contains("#"+submission.taskId()).doesNotContain("agent.genericTask.","PRIVATE FORM DATA");
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?",String.class,submission.applicationId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?",Long.class,submission.taskId())).isEqualTo(action.equals("transfer")?target.id():reviewer.id());
        assertThat(jdbc.queryForObject("SELECT handler_invoked FROM agent_tool_invocation WHERE task_id=? AND decision='ALLOW'",Boolean.class,task.id())).isTrue();
        worker.poll();assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE task_id=? AND action IN ('TRANSFER','CC')",Integer.class,submission.taskId())).isOne();
    }
    @Test void missingConfirmationCannotReachTheHandler()throws Exception{
        var task=plan("transfer",submission.taskId(),target.id(),0);
        assertThat(driver.execute(task,reviewer,null).statusCode()).isEqualTo(409);driver.noHandler(task);unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"transfer","cc"})
    void revokedPermissionAfterQueueIsFailClosed(String action)throws Exception{
        var task=plan(action,submission.taskId(),target.id(),0);String token=driver.confirm(task,reviewer);
        assertThat(driver.execute(task,reviewer,token).statusCode()).isEqualTo(202);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'",tenant,reviewer.role());
        driver.terminal(task,"FAILED");driver.noHandler(task);unchanged();
    }
    @Test void staleVersionAndWrongBusinessCoordinatesNeverMutateTheResource()throws Exception{
        var stale=plan("transfer",submission.taskId(),target.id(),1);driver.run(stale,reviewer,"FAILED");unchanged();
        long leave=LeaveReadFixture.pending(jdbc,tenant,owner.id(),reviewer.id());
        long leaveTask=jdbc.queryForObject("SELECT id FROM workflow_task WHERE business_type='LEAVE_APPLICATION' AND business_id=?",Long.class,leave);
        var wrong=plan("cc",leaveTask,target.id(),0);driver.run(wrong,reviewer,"FAILED");unchanged();
    }
    @Test void realReadToolReturnsOnlyQualifiedCandidatesAndTheirIdentifiers()throws Exception{
        jdbc.update("UPDATE app_user SET display_name=? WHERE id=?","OwnedCandidate"+target.id(),target.id());
        var task=new FrozenReadTaskFixture(jdbc,json,hashing,catalog).create(tenant,reviewer.id(),"todo","approval.genericTask.participants.query",
                json.createObjectNode().put("taskId",submission.taskId()).put("purpose","TRANSFER").put("keyword","OwnedCandidate"+target.id()),FrozenReadTaskFixture.State.QUEUED);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(25)).until(()->{worker.poll();return !List.of("QUEUED","RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()));});
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("SUCCEEDED");
        var result=json.readTree(jdbc.queryForObject("SELECT result::text FROM agent_task_step WHERE task_id=?",String.class,task.id()));
        assertThat(result.path("items")).hasSize(1);assertThat(result.path("items").get(0).path("id").asLong()).isEqualTo(target.id());
        assertThat(result.toString()).doesNotContain("email","avatar","PRIVATE FORM DATA");unchanged();
    }
    @Test void migrationSeedsDisabledContractsAndPreservesExistingReleaseConfiguration(){
        String previous=jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='approval.genericTask.approve'",String.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=false,write_tools_enabled=false WHERE tenant_id=?",tenant);
        jdbc.update("DELETE FROM agent_tool WHERE tenant_id IS NULL AND code IN ('approval.genericTask.participants.query','approval.genericTask.transfer','approval.genericTask.cc')");
        assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version='202611091400'")).isOne();
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(datasource).schemas(ENV.schema()).defaultSchema(ENV.schema()).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isOne();flyway.validate();assertThat(flyway.info().pending()).isEmpty();assertThat(flyway.migrate().migrationsExecuted).isZero();
        for(String suffix:List.of("participants.query","transfer","cc")){
            String code="approval.genericTask."+suffix;
            assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?",Boolean.class,code)).isFalse();
            assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?",String.class,code)).isEqualTo(catalog.find(code).orElseThrow().schemaHash());
        }
        assertThat(jdbc.queryForObject("SELECT enabled OR write_tools_enabled FROM agent_tenant_policy WHERE tenant_id=?",Boolean.class,tenant)).isFalse();
        assertThat(jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='approval.genericTask.approve'",String.class)).isEqualTo(previous);
    }
    @AfterEach void clearFault(){jdbc.execute("DROP TRIGGER IF EXISTS generic_collaboration_invocation_fault ON agent_tool_invocation");jdbc.execute("DROP FUNCTION IF EXISTS generic_collaboration_invocation_fault()");}
    @Test void gatewayPreAuditFailureDoesNotInvokeTheHandler()throws Exception{
        invocationFault(false);var task=plan("cc",submission.taskId(),target.id(),0);
        driver.run(task,reviewer,"FAILED");driver.noHandler(task);unchanged();
    }
    @Test void unknownPostCommitAuditResultIsNeverReplayed()throws Exception{
        invocationFault(true);var task=plan("transfer",submission.taskId(),target.id(),0);
        driver.run(task,reviewer,"PARTIALLY_SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESULT_UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?",Long.class,submission.taskId())).isEqualTo(target.id());
        worker.poll();worker.poll();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE task_id=? AND action='TRANSFER'",Integer.class,submission.taskId())).isOne();
        assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,task.id())).isOne();
    }
    private void invocationFault(boolean after){
        jdbc.execute("CREATE FUNCTION generic_collaboration_invocation_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tool_code IN ('approval.genericTask.transfer','approval.genericTask.cc')"+(after?" AND NEW.outcome='SUCCEEDED'":"")+" THEN RAISE EXCEPTION 'owned collaboration invocation fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER generic_collaboration_invocation_fault BEFORE "+(after?"UPDATE":"INSERT")+" ON agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION generic_collaboration_invocation_fault()");
    }
    private FrozenWriteTaskFixture.Task plan(String suffix,long taskId,long recipient,int version){
        return new FrozenWriteTaskFixture(jdbc,json,hashing,catalog).create(tenant,reviewer.id(),"todo","approval.genericTask."+suffix,
                json.createObjectNode().put("taskId",taskId).put("targetUserId",recipient).put("version",version).put("reason","Explicit user-supplied reason"));
    }
    private void unchanged(){assertThat(jdbc.queryForObject("SELECT version FROM workflow_task WHERE id=?",Integer.class,submission.taskId())).isZero();assertThat(jdbc.queryForObject("SELECT assignee_user_id FROM workflow_task WHERE id=?",Long.class,submission.taskId())).isEqualTo(reviewer.id());}
}
