package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.service.AdminAssetsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SealDecisionAgentIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired AdminAssetsService service;
    @Autowired javax.sql.DataSource datasource;
    @SpyBean AgentTaskWorker worker;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor reviewer,owner;
    private SealDecisionFixture.Usage usage;
    private ConfirmedDecisionFixture driver;
    private long tenant;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        AgentBrowserFixture.registerLocal(p,ENVIRONMENT,AgentBrowserFixture.reserveUiPort(),SECRET);
        p.add("agent.enabled",()->"true");p.add("agent.execution-enabled",()->"true");p.add("agent.write-tools-enabled",()->"true");
        p.add("agent.worker.poll-delay-ms",()->"3600000");p.add("agent.worker.heartbeat-delay-ms",()->"3600000");
    }
    @BeforeEach void setup()throws Exception {
        org.mockito.Mockito.doNothing().when(worker).wakeAfterCommit(org.mockito.ArgumentMatchers.any(com.aiworkmate.agent.task.AgentTaskQueuedEvent.class));
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        reviewer=http.createAndLogin(tenant,List.of("route:seal-usage","seal:read:self","approval:act",
                "agent:tool:seal.query","agent:tool:seal.approve","agent:tool:seal.reject"));
        owner=http.createAndLogin(tenant,List.of("route:seal-usage","seal:read:self","agent:tool:seal.query"));
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?",tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code IN ('seal.query','seal.approve','seal.reject')");
        usage=SealDecisionFixture.pending(jdbc,tenant,owner.id(),reviewer.id());
        driver=new ConfirmedDecisionFixture(jdbc,json,http,worker,hashing,catalog,tenant,"seal-usage");
    }
    @AfterEach void cleanup(){
        jdbc.execute("DROP TRIGGER IF EXISTS seal_decision_audit_fault ON business_audit_log");
        jdbc.execute("DROP TRIGGER IF EXISTS seal_decision_notification_fault ON notification");
        jdbc.execute("DROP TRIGGER IF EXISTS seal_decision_invocation_fault ON agent_tool_invocation");
        jdbc.execute("DROP FUNCTION IF EXISTS seal_decision_fault()");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void confirmsOneDecisionWithSeparateVersionsAndNeverRegistersActualUse(boolean approved)throws Exception {
        var task=plan(approved,usage.taskId(),2,"User reason");driver.run(task,reviewer,"SUCCEEDED");
        var step=driver.detail(task,reviewer).path("steps").get(0);var receipt=step.path("result");
        assertThat(receipt.path("resourceId").asLong()).isEqualTo(usage.id());assertThat(receipt.path("taskId").asLong()).isEqualTo(usage.taskId());
        assertThat(receipt.path("resourceType").asText()).isEqualTo("SEAL_USAGE");
        assertThat(receipt.path("version").asInt()).isEqualTo(3);assertThat(receipt.path("resourceVersion").asInt()).isEqualTo(6);
        assertThat(receipt.path("resourceStatus").asText()).isEqualTo(approved?"APPROVED":"REJECTED");
        assertThat(step.path("resultSummary").asText()).contains("No actual use").doesNotContain("agent.seal.");
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL AND returned_at IS NULL AND actual_copies IS NULL AND handler_user_id IS NULL FROM seal_usage WHERE id=?",Boolean.class,usage.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_instance WHERE id=?",String.class,usage.instanceId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE instance_id=?",Integer.class,usage.instanceId())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seal_usage_document WHERE seal_usage_id=?",Integer.class,usage.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT decision_comment FROM workflow_task WHERE id=?",String.class,usage.taskId())).isEqualTo("User reason");decisionCount(1);
    }
    @Test void readGatewayReturnsTaskCoordinatesOnlyForAnActionableCurrentAssignee()throws Exception {
        JsonNode assigned=read(reviewer,"PENDING");assertThat(assigned.path("items")).hasSize(1);
        var item=assigned.path("items").get(0);assertThat(item.path("taskId").asLong()).isEqualTo(usage.taskId());
        assertThat(item.path("taskVersion").asInt()).isEqualTo(2);assertThat(item.path("version").asInt()).isEqualTo(5);
        JsonNode owned=read(owner,"MINE");assertThat(owned.path("items")).hasSize(1);
        assertThat(owned.path("items").get(0).hasNonNull("taskId")).isFalse();
        jdbc.update("UPDATE workflow_instance SET applicant_id=? WHERE id=?",reviewer.id(),usage.instanceId());
        var invalid=service.listPendingSealUsages(reviewer.id(),tenant,1,20).records().get(0);
        assertThat(invalid.canDecide()).isFalse();assertThat(invalid.taskId()).isNull();assertThat(invalid.taskVersion()).isNull();
    }
    @Test void missingOrWrongConfirmationDoesNotEnqueueTheHandler()throws Exception {
        var task=plan(true,usage.taskId(),2,null);
        assertThat(driver.execute(task,reviewer,null).statusCode()).isEqualTo(409);
        assertThat(driver.execute(task,reviewer,"not-issued").statusCode()).isEqualTo(409);driver.noHandler(task);unchanged();
    }
    @Test void anOldSuccessfulReadRemainsReadableAfterTheToolSchemaUpgrade()throws Exception {
        var output=catalog.find("seal.query").orElseThrow().outputSchema().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)output.at("/properties/items/items/properties")).remove(List.of("taskId","taskVersion"));
        var current=catalog.find("seal.query").orElseThrow();
        var previous=com.aiworkmate.agent.registry.ToolDefinitionFactory.read(com.aiworkmate.agent.registry.ToolCode.SEAL_QUERY,
                current.name(),current.description(),current.purpose(),current.inputSchema(),output,current.requiredPermissions(),
                current.ownershipPolicy(),current.maxResultItems(),current.maxResultBytes(),current.timeoutMs());
        assertThat(previous.schemaHash()).isEqualTo("sha256:9f896eeb4041562e8c0a7d33ed8117af6c9be6f91d71ce3c763a59d3ce3c0175");
        var result=read(reviewer,"PENDING").deepCopy();
        for(JsonNode item:result.path("items"))((com.fasterxml.jackson.databind.node.ObjectNode)item).remove(List.of("taskId","taskVersion"));
        var historical=new FrozenReadTaskFixture(jdbc,json,hashing,new ToolCatalog(List.of(previous))).create(tenant,reviewer.id(),
                "seal-usage","seal.query",json.createObjectNode().put("queue","PENDING"),FrozenReadTaskFixture.State.PLAN_READY);
        jdbc.update("UPDATE agent_task SET status='SUCCEEDED',finished_at=CURRENT_TIMESTAMP WHERE id=?",historical.id());
        jdbc.update("UPDATE agent_task_step SET status='SUCCEEDED',result=?::jsonb,finished_at=CURRENT_TIMESTAMP WHERE task_id=?",result.toString(),historical.id());
        var response=driver.response("GET","/api/ai/tasks/"+historical.number(),reviewer,null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(driver.data(response).path("steps").get(0).path("resultSummary").asText()).contains("Owned seal document","query again").doesNotContain("agent.seal.");
        assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,historical.id())).isZero();
        unchanged();
    }
    @Test void missingReasonIsRejectedWithoutChangingTheBusiness()throws Exception {
        var task=plan(false,usage.taskId(),2,"   ");driver.run(task,reviewer,"FAILED");unchanged();
    }
    @Test void usageVersionAndUsageIdCannotSubstituteForTaskCoordinates()throws Exception {
        var version=plan(true,usage.taskId(),5,"Reason");driver.run(version,reviewer,"FAILED");unchanged();
        var id=plan(true,usage.id(),2,"Reason");driver.run(id,reviewer,"FAILED");unchanged();
    }
    @Test void otherAssigneesAndApplicantsCannotDecide()throws Exception {
        var other=http.createAndLogin(tenant,List.of("route:seal-usage","approval:act","agent:tool:seal.approve"));
        var unrelated=driver.plan(other,"seal.approve",usage.taskId(),2,null);driver.run(unrelated,other,"FAILED");unchanged();
        for(String permission:List.of("approval:act","agent:tool:seal.approve"))jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)",tenant,owner.role(),permission);
        jdbc.update("UPDATE seal_usage SET approver_user_id=? WHERE id=?",owner.id(),usage.id());
        jdbc.update("UPDATE workflow_task SET assignee_user_id=? WHERE id=?",owner.id(),usage.taskId());
        var self=driver.plan(owner,"seal.approve",usage.taskId(),2,null);driver.run(self,owner,"FAILED");unchanged();
    }
    @Test void sameNumberedOtherBusinessAndBrokenInstanceBindingsAreRejected()throws Exception {
        var foreign=SealDecisionFixture.sameNumberedVisitor(jdbc,tenant,owner.id(),reviewer.id(),usage.id());
        long instance=foreign.instanceId(),wrong=foreign.taskId();
        var other=plan(true,wrong,2,null);driver.run(other,reviewer,"FAILED");unchanged();
        jdbc.update("UPDATE workflow_task SET instance_id=? WHERE id=?",instance,usage.taskId());
        var broken=plan(false,usage.taskId(),2,"Reason");driver.run(broken,reviewer,"FAILED");unchanged();
    }
    @Test void permissionRevocationIsEnforcedBeforeEnqueueAndBeforeClaim()throws Exception {
        var task=plan(true,usage.taskId(),2,null);String token=driver.confirm(task,reviewer);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='agent:tool:seal.approve'",tenant,reviewer.role());
        assertThat(driver.execute(task,reviewer,token).statusCode()).isEqualTo(403);driver.noHandler(task);unchanged();
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,'agent:tool:seal.approve')",tenant,reviewer.role());
        var queued=plan(true,usage.taskId(),2,null);assertThat(driver.execute(queued,reviewer,driver.confirm(queued,reviewer)).statusCode()).isEqualTo(202);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'",tenant,reviewer.role());
        driver.terminal(queued,"FAILED");driver.noHandler(queued);unchanged();
    }
    @Test void aTenantChangeCannotUseTheOldConfirmationOrOldDomainContext()throws Exception {
        var task=plan(true,usage.taskId(),2,null);String token=driver.confirm(task,reviewer);
        long foreign=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned tenant') RETURNING id",Long.class,"VD_"+UUID.randomUUID().toString().substring(0,16));
        var foreignAccess=http.createAndLogin(foreign,List.of("route:seal-usage","seal:read:self","approval:act","agent:tool:seal.approve"));
        jdbc.update("UPDATE app_user SET tenant_id=? WHERE id=?",foreign,reviewer.id());
        jdbc.update("INSERT INTO user_role(tenant_id,user_id,role_code) VALUES (?,?,?)",foreign,reviewer.id(),foreignAccess.role());
        assertThat(driver.execute(task,reviewer,token).statusCode()).isIn(403,404);driver.noHandler(task);unchanged();
        assertThatThrownBy(()->service.getSealUsage(reviewer.id(),tenant,usage.id())).isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting(error->((com.aiworkmate.common.BusinessException)error).getErrorCode()).isEqualTo("RESOURCE_FORBIDDEN");
    }
    @Test void concurrentApprovalAndRejectionCommitExactlyOneDecision()throws Exception {
        var a=plan(true,usage.taskId(),2,null);var b=plan(false,usage.taskId(),2,"Reason");
        String ta=driver.confirm(a,reviewer),tb=driver.confirm(b,reviewer);
        var ea=CompletableFuture.supplyAsync(()->executeUnchecked(a,ta));var eb=CompletableFuture.supplyAsync(()->executeUnchecked(b,tb));
        assertThat(ea.join()).isEqualTo(202);assertThat(eb.join()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(30)).until(()->{worker.poll();return driver.isTerminal(a)&&driver.isTerminal(b);});
        assertThat(List.of(driver.status(a),driver.status(b))).containsExactlyInAnyOrder("SUCCEEDED","FAILED");decisionCount(1);
        assertThat(jdbc.queryForObject("SELECT version FROM seal_usage WHERE id=?",Integer.class,usage.id())).isEqualTo(6);
    }
    @Test void closedInstancesAndRepeatedBusinessDecisionsFailClosed()throws Exception {
        jdbc.update("UPDATE workflow_instance SET status='COMPLETED' WHERE id=?",usage.instanceId());
        var closed=plan(true,usage.taskId(),2,null);driver.run(closed,reviewer,"FAILED");unchanged();
        jdbc.update("UPDATE workflow_instance SET status='RUNNING' WHERE id=?",usage.instanceId());
        var first=plan(false,usage.taskId(),2,"Reason");driver.run(first,reviewer,"SUCCEEDED");
        var again=plan(true,usage.taskId(),3,null);driver.run(again,reviewer,"FAILED");decisionCount(1);
    }
    @ParameterizedTest @ValueSource(strings={"audit","notification"})
    void businessAuditAndNotificationFailuresRollBackAllDecisionWrites(String failure)throws Exception {
        if(failure.equals("audit")) {
            jdbc.execute("CREATE FUNCTION seal_decision_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_type='SEAL_USAGE' THEN RAISE EXCEPTION 'owned audit fault'; END IF; RETURN NEW; END $$");
            jdbc.execute("CREATE TRIGGER seal_decision_audit_fault BEFORE INSERT ON business_audit_log FOR EACH ROW EXECUTE FUNCTION seal_decision_fault()");
        } else {
            jdbc.execute("CREATE FUNCTION seal_decision_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.biz_type='seal' THEN RAISE EXCEPTION 'owned notification fault'; END IF; RETURN NEW; END $$");
            jdbc.execute("CREATE TRIGGER seal_decision_notification_fault BEFORE INSERT ON notification FOR EACH ROW EXECUTE FUNCTION seal_decision_fault()");
        }
        var task=plan(true,usage.taskId(),2,null);driver.run(task,reviewer,"PARTIALLY_SUCCEEDED");unchanged();
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_instance WHERE id=?",String.class,usage.instanceId())).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE biz_type='seal' AND biz_id=?",Integer.class,usage.id())).isZero();
        worker.poll();assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,task.id())).isOne();
    }
    @Test void unknownPostInvocationResultNeverReplaysTheDecision()throws Exception {
        invocationFault("UPDATE"," AND NEW.outcome='SUCCEEDED'");
        var task=plan(true,usage.taskId(),2,null);driver.run(task,reviewer,"PARTIALLY_SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("TOOL_RESULT_UNKNOWN");
        worker.poll();worker.poll();decisionCount(1);assertThat(jdbc.queryForObject("SELECT tool_call_count FROM agent_task WHERE id=?",Integer.class,task.id())).isOne();
    }
    @Test void preInvocationAuditFailureAndFrozenArgumentTamperingNeverReachADomainHandler()throws Exception {
        invocationFault("INSERT","");var task=plan(true,usage.taskId(),2,null);driver.run(task,reviewer,"FAILED");driver.noHandler(task);unchanged();
        cleanup();var changed=plan(true,usage.taskId(),2,null);assertThat(driver.execute(changed,reviewer,driver.confirm(changed,reviewer)).statusCode()).isEqualTo(202);
        jdbc.update("UPDATE agent_task_step SET args=jsonb_set(args,'{version}','5'::jsonb) WHERE task_id=?",changed.id());driver.terminal(changed,"FAILED");driver.noHandler(changed);unchanged();
    }
    @Test void migrationAddsDisabledWritesAndUpgradesOnlyTheKnownSealReadSchema() {
        var flyway=Flyway.configure().dataSource(datasource).schemas(ENVIRONMENT.schema()).defaultSchema(ENVIRONMENT.schema()).locations("classpath:db/migration").load();
        flyway.validate();assertThat(flyway.info().pending()).isEmpty();
        String previous=jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='visitor.query'",String.class);
        jdbc.update("DELETE FROM agent_tool WHERE tenant_id IS NULL AND code IN ('seal.approve','seal.reject')");
        jdbc.update("UPDATE agent_tool SET output_schema=jsonb_set(output_schema,'{properties,items,items,properties}',(output_schema#>'{properties,items,items,properties}')-'taskId'-'taskVersion'),schema_hash='sha256:9f896eeb4041562e8c0a7d33ed8117af6c9be6f91d71ce3c763a59d3ce3c0175' WHERE tenant_id IS NULL AND code='seal.query'");
        jdbc.update("UPDATE agent_tenant_policy SET enabled=false,write_tools_enabled=false WHERE tenant_id=?",tenant);
        int replay=jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version::numeric>=202611091200",Integer.class);
        assertThat(replay).isPositive();assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version::numeric>=202611091200")).isEqualTo(replay);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(replay);flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
        for(String code:List.of("seal.approve","seal.reject","seal.query"))assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?",String.class,code)).isEqualTo(catalog.find(code).orElseThrow().schemaHash());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool WHERE code IN ('seal.approve','seal.reject') AND enabled",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT enabled OR write_tools_enabled FROM agent_tenant_policy WHERE tenant_id=?",Boolean.class,tenant)).isFalse();
        assertThat(jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='visitor.query'",String.class)).isEqualTo(previous);
    }
    private FrozenWriteTaskFixture.Task plan(boolean approved,long task,int version,String reason){return driver.plan(reviewer,approved?"seal.approve":"seal.reject",task,version,reason);}
    private int executeUnchecked(FrozenWriteTaskFixture.Task task,String token){try{return driver.execute(task,reviewer,token).statusCode();}catch(Exception e){throw new IllegalStateException("Owned seal document execution failed",e);}}
    private void unchanged(){assertThat(jdbc.queryForObject("SELECT status='PENDING' AND version=5 FROM seal_usage WHERE id=?",Boolean.class,usage.id())).isTrue();assertThat(jdbc.queryForObject("SELECT status='PENDING' AND version=2 FROM workflow_task WHERE id=?",Boolean.class,usage.taskId())).isTrue();decisionCount(0);}
    private void decisionCount(int count){assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE task_id=? AND action IN ('APPROVE','REJECT')",Integer.class,usage.taskId())).isEqualTo(count);assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='SEAL_USAGE' AND resource_id=? AND action IN ('APPROVE','REJECT') AND result='SUCCESS'",Integer.class,Long.toString(usage.id()))).isEqualTo(count);}
    private void invocationFault(String operation,String suffix){jdbc.execute("CREATE FUNCTION seal_decision_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tool_code IN ('seal.approve','seal.reject')"+suffix+" THEN RAISE EXCEPTION 'owned invocation fault'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER seal_decision_invocation_fault BEFORE "+operation+" ON agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION seal_decision_fault()");}
    private JsonNode read(AgentHttpTestFixture.Actor actor,String queue)throws Exception {
        var task=new FrozenReadTaskFixture(jdbc,json,hashing,catalog).create(tenant,actor.id(),"seal-usage","seal.query",json.createObjectNode().put("queue",queue),FrozenReadTaskFixture.State.PLAN_READY);
        var body=json.createObjectNode().put("planVersion",1).put("planHash",task.planHash()).toString();
        assertThat(driver.response("POST","/api/ai/tasks/"+task.number()+"/execute",actor,body).statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(25)).until(()->{worker.poll();return "SUCCEEDED".equals(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()));});
        return driver.data(driver.response("GET","/api/ai/tasks/"+task.number(),actor,null)).path("steps").get(0).path("result");
    }
}
