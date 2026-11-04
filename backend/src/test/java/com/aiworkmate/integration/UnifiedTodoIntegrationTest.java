package com.aiworkmate.integration;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.GenericApprovalService;
import com.aiworkmate.service.TodoQueryService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnifiedTodoIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    private static final String OLD_HASH="sha256:5587d07883805ecb5810979e984dc33044c68898a9e9aeed4d07c4f3a9793c69";
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired GenericApprovalService approvals;
    @Autowired TodoQueryService queries;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired AgentTaskWorker worker;
    @Autowired AgentRuntimeProperties runtime;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor reviewer,owner;
    private long tenant,leaveTask,genericTask,genericApplication;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        AgentBrowserFixture.registerLocal(p,ENVIRONMENT,AgentBrowserFixture.reserveUiPort(),SECRET);
        p.add("agent.enabled",()->"true");p.add("agent.execution-enabled",()->"true");
        p.add("agent.worker.poll-delay-ms",()->"3600000");p.add("agent.worker.heartbeat-delay-ms",()->"3600000");
    }
    @BeforeEach void setup() throws Exception {
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        // Release only this L0 read in the disposable database; every write gate stays closed.
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?",tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='todo.query'");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id='todo' AND tool_code='todo.query'",tenant);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"zh-CN");
        reviewer=http.createAndLogin(tenant,List.of("todo:read","approval:act","route:todo","agent:tool:todo.query"));
        owner=http.createAndLogin(tenant,List.of("route:approval-start","approval:create","approval:submit","approval:withdraw"));
        long leave=LeaveReadFixture.pending(jdbc,tenant,owner.id(),reviewer.id());
        leaveTask=jdbc.queryForObject("SELECT id FROM workflow_task WHERE business_type='LEAVE_APPLICATION' AND business_id=?",Long.class,leave);
        String key="unified_"+UUID.randomUUID().toString().replace("-","");
        long form=jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status) VALUES (?,?,'Frozen generic title','{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}','ENABLED') RETURNING id",Long.class,tenant,key);
        jdbc.update("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status) VALUES (?,?,'Review',?,?,'ENABLED')",tenant,key,form,
                "[{\"nodeName\":\"Real review node\",\"approveType\":\"USER\",\"targetKey\":\""+reviewer.id()+"\"}]");
        genericApplication=approvals.createDraft(owner.id(),new ApprovalDraftRequest(key,key,Map.of("reason","PRIVATE APPLICATION PAYLOAD"))).id();
        genericTask=approvals.submitDraft(owner.id(),genericApplication,new VersionRequest(0)).taskId();
    }
    @Test void returnsBothBusinessesWithOneRealPaginationAndWithoutPayloadOrStoragePaths() throws Exception {
        jdbc.update("UPDATE app_user SET avatar='SYNTHETIC-INTERNAL-STORAGE-KEY' WHERE id=?",owner.id());
        var response=get(reviewer,"/api/todos",200); assertThat(response.path("total").asInt()).isEqualTo(2);
        assertThat(response.path("businessType").asText()).isEqualTo("ALL");assertThat(response.path("status").asText()).isEqualTo("PENDING");
        var types=new HashSet<String>();response.path("records").forEach(row->{types.add(row.path("businessType").asText());
            assertThat(row.has("applicantAvatar")).isFalse();assertThat(row.has("applicantUpdatedAt")).isFalse();
            assertThat(row.path("applicantAvatarUrl").asText()).startsWith("/api/users/"+owner.id()+"/avatar/content?v=");});
        assertThat(types).containsExactlyInAnyOrder("LEAVE_APPLICATION","GENERIC_APPROVAL");
        assertThat(response.toString()).contains("Frozen generic title","Real review node").doesNotContain("PRIVATE APPLICATION PAYLOAD","SYNTHETIC-INTERNAL-STORAGE-KEY");
        var generic=get(reviewer,"/api/todos?businessType=GENERIC_APPROVAL",200);assertThat(generic.path("total").asInt()).isOne();
        assertThat(generic.path("records").get(0).path("id").asLong()).isEqualTo(genericTask);
        assertThat(generic.path("records").get(0).path("durationHalfDays").isNull()).isTrue();
    }
    @Test void paginatesAcrossBusinessKindsInsteadOfConcatenatingTwoIndependentPages() throws Exception {
        for(int i=0;i<21;i++)LeaveReadFixture.pending(jdbc,tenant,owner.id(),reviewer.id());
        var ids=new HashSet<Long>();
        for(int page=1;page<=2;page++){
            var data=get(reviewer,"/api/todos?page="+page+"&size=20",200);assertThat(data.path("total").asInt()).isEqualTo(23);
            assertThat(data.path("records")).hasSize(page==1?20:3);data.path("records").forEach(row->assertThat(ids.add(row.path("id").asLong())).isTrue());
        }
        assertThat(ids).hasSize(23).contains(genericTask);
    }
    @Test void readsProtectedSummariesAndKeepsTheLeaveDetailEndpointTypeSpecific() throws Exception {
        var generic=get(reviewer,"/api/todos/"+genericTask+"/summary",200);assertThat(generic.path("businessType").asText()).isEqualTo("GENERIC_APPROVAL");
        assertThat(generic.path("applicationId").asLong()).isEqualTo(genericApplication);
        get(reviewer,"/api/todos/"+genericTask,404);get(reviewer,"/api/todos/"+leaveTask,200);
        get(owner,"/api/todos/"+genericTask+"/summary",403);
    }
    @Test void rejectsUnrelatedTenantsAndActorsAndRechecksRevocationWithTheSameCookie() throws Exception {
        var stranger=http.createAndLogin(tenant,List.of("todo:read"));
        assertThat(get(stranger,"/api/todos",200).path("total").asInt()).isZero();get(stranger,"/api/todos/"+genericTask+"/summary",404);
        long foreign=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned foreign tenant') RETURNING id",Long.class,"UT_"+UUID.randomUUID().toString().substring(0,16));
        var actor=http.createAndLogin(foreign,List.of("todo:read"));get(actor,"/api/todos/"+genericTask+"/summary",404);
        assertThatThrownBy(()->queries.mine(reviewer.id(),foreign,"ALL",null,null,null,1,20)).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_FORBIDDEN");
        assertThat(get(reviewer,"/api/todos?tenantId="+foreign+"&userId="+stranger.id(),200).path("total").asInt()).isEqualTo(2);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='todo:read'",tenant,reviewer.role());
        get(reviewer,"/api/todos",403);get(reviewer,"/api/todos/"+genericTask+"/summary",403);get(null,"/api/todos",401);
    }
    @Test void neverUsesAnEditedDraftAsAnOlderTasksSummary() throws Exception {
        jdbc.update("UPDATE approval_application SET title='PRIVATE NEW DRAFT TITLE',data_json='{\"reason\":\"PRIVATE NEW DRAFT\"}' WHERE id=?",genericApplication);
        var original=get(reviewer,"/api/todos/"+genericTask+"/summary",200);assertThat(original.toString()).contains("Frozen generic title").doesNotContain("PRIVATE NEW DRAFT");
        jdbc.update("DELETE FROM approval_execution WHERE application_id=?",genericApplication);
        var unavailable=get(reviewer,"/api/todos/"+genericTask+"/summary",200);assertThat(unavailable.path("detailAvailable").asBoolean()).isFalse();
        assertThat(unavailable.path("title").asText()).startsWith("GA-");assertThat(unavailable.toString()).doesNotContain("PRIVATE NEW DRAFT");
        jdbc.update("UPDATE workflow_instance SET business_id=business_id+100000 WHERE id=(SELECT instance_id FROM workflow_task WHERE id=?)",genericTask);
        assertThat(get(reviewer,"/api/todos",200).path("total").asInt()).isOne();get(reviewer,"/api/todos/"+genericTask+"/summary",404);
    }
    @Test void validatesEveryFilterAndReturnsTheAppliedScope() throws Exception {
        for(String query:List.of("?size=51","?page=0","?page=10001","?businessType=HR","?status=WAITING","?from=2026-11-04T12:00:00&to=2026-11-01T00:00:00"))get(reviewer,"/api/todos"+query,400);
        jdbc.update("UPDATE workflow_task SET status='APPROVED',completed_at=CURRENT_TIMESTAMP WHERE id=?",leaveTask);
        assertThat(get(reviewer,"/api/todos",200).path("total").asInt()).isOne();
        var all=get(reviewer,"/api/todos?status=ALL&businessType=LEAVE_APPLICATION",200);assertThat(all.path("status").asText()).isEqualTo("ALL");assertThat(all.path("total").asInt()).isOne();
        assertThat(get(reviewer,"/api/todos?from=2099-01-01T00:00:00",200).path("total").asInt()).isZero();
    }
    @Test void executesTheRealUnifiedReadThroughWorkerGatewayAndProducesSpecificTextWithoutBusinessWrites() throws Exception {
        var task=queue(json.createObjectNode());String status=run(task);
        assertThat(status).as("owned task error=%s step error=%s gateway decisions=%s",jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id()),
                jdbc.queryForObject("SELECT error_code FROM agent_task_step WHERE task_id=?",String.class,task.id()),
                jdbc.queryForList("SELECT decision_code FROM agent_tool_invocation WHERE task_id=?",String.class,task.id())).isEqualTo("SUCCEEDED");
        var result=json.readTree(jdbc.queryForObject("SELECT result::text FROM agent_task_step WHERE task_id=?",String.class,task.id()));
        assertThat(result.path("items")).hasSize(2);assertThat(result.path("businessType").asText()).isEqualTo("ALL");
        var delivered=get(reviewer,"/api/ai/tasks/"+task.number(),200).path("steps").get(0);
        assertThat(delivered.path("result")).isEqualTo(result);
        assertThat(delivered.path("resultSummary").asText()).contains("Frozen generic title","Real review node").doesNotContain("PRIVATE APPLICATION PAYLOAD","agent.todo.","agent.result.");
        assertThat(jdbc.queryForObject("SELECT handler_invoked FROM agent_tool_invocation WHERE task_id=? AND decision='ALLOW'",Boolean.class,task.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_task WHERE id IN (?,?) AND status='PENDING' AND version=0",Integer.class,leaveTask,genericTask)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?",Integer.class,genericApplication)).isOne();
        assertThat(runtime.isWriteToolsEnabled()).isFalse();
    }
    @Test void rejectsOldContractsForgedIdentityAndRevokedReadPermissionBeforeTheHandler() throws Exception {
        var invalid=queue(json.createObjectNode().put("tenantId",tenant));assertThat(run(invalid)).isEqualTo("FAILED");noHandler(invalid);
        var old=queue(json.createObjectNode());var plan=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(jdbc.queryForObject("SELECT plan::text FROM agent_task WHERE id=?",String.class,old.id()));
        ((com.fasterxml.jackson.databind.node.ObjectNode)plan.path("steps").get(0)).put("schemaHash",OLD_HASH);
        jdbc.update("UPDATE agent_task SET plan=?::jsonb,plan_hash=? WHERE id=?",plan.toString(),hashing.hash(plan),old.id());
        jdbc.update("UPDATE agent_task_step SET schema_hash=? WHERE task_id=?",OLD_HASH,old.id());assertThat(run(old)).isEqualTo("FAILED");noHandler(old);
        var revoked=queue(json.createObjectNode());jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='todo:read'",tenant,reviewer.role());
        assertThat(run(revoked)).isEqualTo("FAILED");noHandler(revoked);
        var disabled=queue(json.createObjectNode());jdbc.update("UPDATE agent_tool SET enabled=false WHERE tenant_id IS NULL AND code='todo.query'");
        assertThat(run(disabled)).isEqualTo("FAILED");noHandler(disabled);
        assertThat(jdbc.queryForList("SELECT decision_code FROM agent_tool_invocation WHERE task_id=?",String.class,disabled.id())).containsExactly("TOOL_DISABLED");
    }
    private JsonNode get(AgentHttpTestFixture.Actor actor,String path,int expected) throws Exception {
        var response=AgentHttpTestFixture.client().send(http.request("GET",path,actor,null,null).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("owned GET %s",path).isEqualTo(expected);return json.readTree(response.body()).path("data");
    }
    private FrozenReadTaskFixture.Task queue(JsonNode args){return new FrozenReadTaskFixture(jdbc,json,hashing,catalog).create(tenant,reviewer.id(),"todo","todo.query",args,FrozenReadTaskFixture.State.QUEUED);}
    private String run(FrozenReadTaskFixture.Task task){worker.poll();await().atMost(Duration.ofSeconds(25)).until(()->{worker.poll();return !List.of("QUEUED","RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()));});return jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id());}
    private void noHandler(FrozenReadTaskFixture.Task task){assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Integer.class,task.id())).isZero();}
}
