package com.aiworkmate.integration;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.service.GenericApprovalTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
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
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenericTaskDetailAgentIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString()+UUID.randomUUID();
    private static final String CODE = "approval.genericTask.detail.query";
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired AgentTaskWorker worker;
    @Autowired AgentRuntimeProperties runtime;
    @Autowired GenericApprovalTaskService service;
    @Autowired javax.sql.DataSource datasource;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor reviewer, owner;
    private GenericTaskReadFixture.Submission submission;
    private long tenant;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        AgentBrowserFixture.registerLocal(p,ENVIRONMENT,AgentBrowserFixture.reserveUiPort(),SECRET);
        p.add("agent.enabled",()->"true");p.add("agent.execution-enabled",()->"true");
        p.add("agent.worker.poll-delay-ms",()->"3600000");p.add("agent.worker.heartbeat-delay-ms",()->"3600000");
    }
    @BeforeEach void setup() throws Exception {
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"zh-CN");
        reviewer=http.createAndLogin(tenant,List.of("route:todo","todo:read","approval:act","agent:tool:"+CODE));
        owner=http.createAndLogin(tenant,List.of("route:todo","todo:read","agent:tool:"+CODE));
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?",tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?",CODE);
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id='todo' AND tool_code=?",tenant,CODE);
        submission=GenericTaskReadFixture.pending(jdbc,json,tenant,owner.id(),reviewer.id(),"Frozen task title",
                "{\"fields\":[{\"name\":\"reason\",\"label\":\"Reason\",\"type\":\"textarea\"},{\"name\":\"token\",\"type\":\"text\"},{\"name\":\"attachment\",\"type\":\"upload\"},{\"name\":\"choice\",\"type\":\"checkbox\"},{\"name\":\"amount\",\"type\":\"money\"},{\"name\":\"tail\",\"type\":\"text\"}]}",
                "{\"reason\":\"Frozen reason\",\"token\":\"SYNTHETIC PRIVATE VALUE\",\"attachment\":\"INTERNAL ATTACHMENT KEY\",\"choice\":[\"a\",\"b\"],\"amount\":9007199254740993.01,\"tail\":\"Last frozen field\",\"unregistered\":\"UNREGISTERED PRIVATE VALUE\"}");
    }
    @Test void returnsSpecificFrozenFieldsAndNodeCapabilitiesThroughRealGatewayAndHttp() throws Exception {
        var task=queue(reviewer,submission.taskId(),1);assertThat(run(task)).isEqualTo("SUCCEEDED");
        var delivered=get(reviewer,"/api/ai/tasks/"+task.number(),200).path("steps").get(0);
        var result=delivered.path("result");assertThat(result.path("taskId").asLong()).isEqualTo(submission.taskId());
        assertThat(result.path("fields")).hasSize(4);assertThat(result.path("fieldsTotal").asInt()).isEqualTo(6);
        assertThat(result.path("fields").get(1).path("redacted").asBoolean()).isTrue();
        assertThat(result.path("fields").get(2).path("values")).isEmpty();
        assertThat(result.path("canApprove").asBoolean()).isTrue();assertThat(result.path("canReject").asBoolean()).isTrue();
        assertThat(delivered.path("resultSummary").asText()).contains("Frozen task title","Frozen review node","Frozen reason","fieldPage=2").doesNotContain("agent.genericTask.","agent.application.");
        assertThat(result.toString()).doesNotContain("SYNTHETIC PRIVATE VALUE","INTERNAL ATTACHMENT KEY","UNREGISTERED PRIVATE VALUE","formSchema","planJson");
        assertThat(jdbc.queryForObject("SELECT handler_invoked FROM agent_tool_invocation WHERE task_id=? AND decision='ALLOW'",Boolean.class,task.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT status='PENDING' AND version=0 FROM workflow_task WHERE id=?",Boolean.class,submission.taskId())).isTrue();
        assertThat(runtime.isWriteToolsEnabled()).isFalse();
    }
    @Test void pagesAllFieldsAndPreservesLargeDecimalValuesWithoutReadingEditedData() throws Exception {
        jdbc.update("UPDATE approval_application SET title='NEW PRIVATE TITLE',data_json='{\"reason\":\"NEW PRIVATE DRAFT\"}',status='DRAFT' WHERE id=?",submission.applicationId());
        jdbc.update("UPDATE approval_form SET schema_json='{\"fields\":[{\"name\":\"unregistered\",\"type\":\"text\"}]}' WHERE id=?",submission.formId());
        var first=service.detailAssignedForAgent(reviewer.id(),tenant,submission.taskId(),1);
        var second=service.detailAssignedForAgent(reviewer.id(),tenant,submission.taskId(),2);
        assertThat(first.task().title()).isEqualTo("Frozen task title");assertThat(first.canApprove()).isFalse();
        assertThat(first.fields().get(0).values()).containsExactly("Frozen reason");
        assertThat(second.fields().get(0).values()).containsExactly("9007199254740993.01");
        assertThat(second.fields().get(1).values()).containsExactly("Last frozen field");
        assertThat(service.detailAssignedForAgent(reviewer.id(),tenant,submission.taskId(),25).fields()).isEmpty();
        assertThat(service.detail(reviewer.id(),tenant,submission.taskId(),1).fields()).hasSize(6);
    }
    @Test void onlyAssignedActorCanReadEvenWhenApplicantOrAuditorHasTheTool() throws Exception {
        assertThat(run(queue(owner,submission.taskId(),1))).isEqualTo("FAILED");
        var auditor=http.createAndLogin(tenant,List.of("route:todo","todo:read","audit:read","agent:tool:"+CODE));
        assertThat(run(queue(auditor,submission.taskId(),1))).isEqualTo("FAILED");
        assertThatThrownBy(()->service.detailAssignedForAgent(reviewer.id(),tenant+100000,submission.taskId(),1)).isInstanceOf(BusinessException.class);
        long foreign=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned foreign read tenant') RETURNING id",Long.class,"GTD_"+UUID.randomUUID().toString().substring(0,16));
        var stranger=http.createAndLogin(foreign,List.of("todo:read"));
        assertThatThrownBy(()->service.detailAssignedForAgent(stranger.id(),foreign,submission.taskId(),1)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsMissingOrMismatchedSnapshotAndWrongBusinessTask() {
        jdbc.update("UPDATE approval_execution SET applicant_user_id=? WHERE instance_id=?",reviewer.id(),submission.instanceId());
        assertThatThrownBy(()->service.detailAssignedForAgent(reviewer.id(),tenant,submission.taskId(),1)).isInstanceOf(BusinessException.class);
        jdbc.update("DELETE FROM approval_execution WHERE instance_id=?",submission.instanceId());
        assertThat(run(queue(reviewer,submission.taskId(),1))).isEqualTo("FAILED");
        long leave=LeaveReadFixture.pending(jdbc,tenant,owner.id(),reviewer.id());
        long leaveTask=jdbc.queryForObject("SELECT id FROM workflow_task WHERE business_type='LEAVE_APPLICATION' AND business_id=?",Long.class,leave);
        assertThatThrownBy(()->service.detailAssignedForAgent(reviewer.id(),tenant,leaveTask,1)).isInstanceOf(BusinessException.class);
    }
    @Test void rechecksBusinessPermissionAfterQueueingAndDoesNotInvokeTheHandler() throws Exception {
        var task=queue(reviewer,submission.taskId(),1);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='todo:read'",tenant,reviewer.role());
        assertThat(run(task)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Integer.class,task.id())).isZero();
        assertThatThrownBy(()->service.detailAssignedForAgent(reviewer.id(),tenant,submission.taskId(),1)).isInstanceOf(BusinessException.class);
        get(reviewer,"/api/todos",403);
    }
    @Test void rereadsCurrentWriteCapabilitiesAndValidatesEveryPageBoundary() {
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'",tenant,reviewer.role());
        var result=service.detailAssignedForAgent(reviewer.id(),tenant,submission.taskId(),1);
        assertThat(result.canApprove()).isFalse();assertThat(result.canReject()).isFalse();
        for(int page:new int[]{0,26})assertThatThrownBy(()->service.detailAssignedForAgent(reviewer.id(),tenant,submission.taskId(),page)).isInstanceOf(BusinessException.class);
    }
    @Test void supportsAllOneHundredFieldsWithoutDuplicatesOrSilentTruncation() {
        var schema=json.createObjectNode();var definitions=schema.putArray("fields");var data=json.createObjectNode();
        for(int i=0;i<100;i++){definitions.addObject().put("name","f"+i).put("type","text");data.put("f"+i,"Value "+i);}
        jdbc.update("UPDATE approval_execution SET form_schema=?,data_json=? WHERE instance_id=?",schema.toString(),data.toString(),submission.instanceId());
        var names=new HashSet<String>();
        for(int page=1;page<=25;page++){
            var result=service.detailAssignedForAgent(reviewer.id(),tenant,submission.taskId(),page);
            assertThat(result.totalFields()).isEqualTo(100);assertThat(result.fields()).hasSize(4);
            result.fields().forEach(field->assertThat(names.add(field.name())).isTrue());
        }
        assertThat(names).hasSize(100);
    }
    @Test void validatesFreshAndUpgradedMigrationWithoutChangingExistingPoliciesOrTools() throws Exception {
        var tool=catalog.find(CODE).orElseThrow();assertThat(jdbc.queryForObject("SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?",String.class,CODE)).isEqualTo(tool.schemaHash());
        var flyway=Flyway.configure().dataSource(datasource).schemas(ENVIRONMENT.schema()).defaultSchema(ENVIRONMENT.schema()).locations("classpath:db/migration").load();
        flyway.validate();assertThat(flyway.info().pending()).isEmpty();
        String previous=jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='todo.query'",String.class);
        jdbc.update("UPDATE agent_tool SET enabled=false WHERE tenant_id IS NULL AND code=?",CODE);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=false,write_tools_enabled=false WHERE tenant_id=?",tenant);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?",tenant,reviewer.role(),"agent:tool:"+CODE);
        int replay=jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version::numeric>=202611041430",Integer.class);
        assertThat(replay).isPositive();assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version::numeric>=202611041430")).isEqualTo(replay);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(replay);flyway.validate();assertThat(flyway.info().pending()).isEmpty();assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT enabled FROM agent_tool WHERE tenant_id IS NULL AND code=?",Boolean.class,CODE)).isFalse();
        assertThat(jdbc.queryForObject("SELECT enabled OR write_tools_enabled FROM agent_tenant_policy WHERE tenant_id=?",Boolean.class,tenant)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?",Integer.class,tenant,reviewer.role(),"agent:tool:"+CODE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT to_jsonb(t)::text FROM agent_tool t WHERE tenant_id IS NULL AND code='todo.query'",String.class)).isEqualTo(previous);
    }
    private FrozenReadTaskFixture.Task queue(AgentHttpTestFixture.Actor actor,long taskId,int fieldPage){
        return new FrozenReadTaskFixture(jdbc,json,hashing,catalog).create(tenant,actor.id(),"todo",CODE,json.createObjectNode().put("taskId",taskId).put("fieldPage",fieldPage),FrozenReadTaskFixture.State.QUEUED);
    }
    private String run(FrozenReadTaskFixture.Task task){worker.poll();await().atMost(Duration.ofSeconds(25)).until(()->{worker.poll();return !List.of("QUEUED","RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()));});return jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id());}
    private JsonNode get(AgentHttpTestFixture.Actor actor,String path,int expected) throws Exception {
        var response=AgentHttpTestFixture.client().send(http.request("GET",path,actor,null,null).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("owned GET %s",path).isEqualTo(expected);return json.readTree(response.body()).path("data");
    }
}
