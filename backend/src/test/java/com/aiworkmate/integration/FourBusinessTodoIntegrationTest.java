package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Authenticated projections against owned PostgreSQL and Redis, with live database permissions. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class FourBusinessTodoIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment(){return ENV;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolCatalog catalog;
    @Autowired AgentHashing hashing;
    @Autowired AgentTaskWorker worker;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor reviewer,owner;
    private long tenant;
    private VisitorDecisionFixture.Booking visitor;
    private SealDecisionFixture.Usage seal;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        AgentBrowserFixture.registerLocal(p,ENV,AgentBrowserFixture.reserveUiPort(),SECRET);
        p.add("agent.enabled",()->"true");p.add("agent.execution-enabled",()->"true");
        p.add("agent.worker.poll-delay-ms",()->"3600000");p.add("agent.worker.heartbeat-delay-ms",()->"3600000");
    }
    @BeforeEach void setup() throws Exception {
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        var permissions=List.of("todo:read","dashboard:read","data:export","route:todo","visitor:read:self","seal:read:self","agent:tool:todo.query");
        reviewer=http.createAndLogin(tenant,permissions);owner=http.createAndLogin(tenant,permissions);
        LeaveReadFixture.pending(jdbc,tenant,owner.id(),reviewer.id());
        GenericTaskReadFixture.pending(jdbc,json,tenant,owner.id(),reviewer.id(),"Frozen generic title",
                "{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}","{\"reason\":\"PRIVATE FORM\"}");
        visitor=VisitorDecisionFixture.pending(jdbc,tenant,owner.id(),reviewer.id());
        seal=SealDecisionFixture.pending(jdbc,tenant,owner.id(),reviewer.id());
        jdbc.update("UPDATE visitor_booking SET purpose='PRIVATE PURPOSE',visitor_phone='PRIVATE PHONE' WHERE id=?",visitor.id());
        jdbc.update("UPDATE seal_usage SET usage_reason='PRIVATE REASON' WHERE id=?",seal.id());
    }
    @Test void mixedPagingFiltersSummaryMetricsAndExportShareFourBusinesses() throws Exception {
        var first=list("?size=2");var second=list("?page=2&size=2");
        assertThat(first.path("total").asInt()).isEqualTo(4);assertThat(first.path("records")).hasSize(2);
        assertThat(second.path("total").asInt()).isEqualTo(4);assertThat(second.path("records")).hasSize(2);
        var ids=new java.util.HashSet<Long>();
        for(var page:List.of(first,second))for(var row:page.path("records")){
            assertThat(ids.add(row.path("id").asLong())).isTrue();
            var summary=request("GET","/api/todos/"+row.path("id").asLong()+"/summary",reviewer,null,200);
            assertThat(summary.path("businessType")).isEqualTo(row.path("businessType"));
            assertThat(summary.path("applicationId")).isEqualTo(row.path("applicationId"));
        }
        for(String type:List.of("LEAVE_APPLICATION","GENERIC_APPROVAL","VISITOR_BOOKING","SEAL_USAGE")){
            var page=list("?businessType="+type);assertThat(page.path("total").asInt()).isOne();
            assertThat(page.path("records").get(0).path("businessType").asText()).isEqualTo(type);
        }
        assertConsistent(4);
        assertThat(list("?status=REJECTED").path("total").asInt()).isZero();
        request("GET","/api/todos?businessType=UNKNOWN",reviewer,null,400);
        request("GET","/api/todos?size=51",reviewer,null,400);
    }
    @Test void liveDomainRevocationFiltersAllAndDeniesExplicitReadWithoutRelogin() throws Exception {
        revoke("visitor:read:self");assertConsistent(3);
        request("GET","/api/todos?businessType=VISITOR_BOOKING",reviewer,null,403);
        request("GET","/api/todos/"+visitor.taskId()+"/summary",reviewer,null,404);
        revoke("seal:read:self");assertConsistent(2);
        request("GET","/api/todos?businessType=SEAL_USAGE",reviewer,null,403);
        request("GET","/api/todos/"+seal.taskId()+"/summary",reviewer,null,404);
        revoke("todo:read");request("GET","/api/todos",reviewer,null,403);
    }
    @Test void malformedResourceAndInstanceBindingsNeverInflateAnyProjection() throws Exception {
        jdbc.update("UPDATE visitor_booking SET workflow_instance_id=NULL WHERE id=?",visitor.id());assertConsistent(3);
        jdbc.update("UPDATE seal_usage SET approver_user_id=? WHERE id=?",owner.id(),seal.id());assertConsistent(2);
        request("GET","/api/todos/"+visitor.taskId()+"/summary",reviewer,null,404);
        request("GET","/api/todos/"+seal.taskId()+"/summary",reviewer,null,404);
    }
    @Test void closedInstancesAndMismatchedBusinessStatesAreNotPendingTodos() throws Exception {
        jdbc.update("UPDATE workflow_instance SET status='COMPLETED' WHERE id=?",visitor.instanceId());assertConsistent(3);
        jdbc.update("UPDATE seal_usage SET status='APPROVED' WHERE id=?",seal.id());assertConsistent(2);
    }
    @Test void terminalBusinessLifecycleKeepsAssignedApprovalHistoryReadable() throws Exception {
        jdbc.update("UPDATE workflow_task SET status='APPROVED' WHERE id IN (?,?)",visitor.taskId(),seal.taskId());
        jdbc.update("UPDATE workflow_instance SET status='COMPLETED' WHERE id IN (?,?)",visitor.instanceId(),seal.instanceId());
        for(String state:List.of("APPROVED","CHECKED_IN","VISITED","LEFT","NO_SHOW")){
            jdbc.update("UPDATE visitor_booking SET status=? WHERE id=?",state,visitor.id());
            assertThat(list("?businessType=VISITOR_BOOKING&status=APPROVED").path("total").asInt()).isOne();
        }
        for(String state:List.of("APPROVED","USED","RETURNED")){
            jdbc.update("UPDATE seal_usage SET status=? WHERE id=?",state,seal.id());
            assertThat(list("?businessType=SEAL_USAGE&status=APPROVED").path("total").asInt()).isOne();
        }
        assertConsistent(2);
        assertThat(list("?status=ALL").path("total").asInt()).isEqualTo(4);
    }
    @Test void otherAssigneesTenantsAndAnonymousRequestsCannotSeeRecords() throws Exception {
        assertThat(request("GET","/api/todos",owner,null,200).path("total").asInt()).isZero();
        request("GET","/api/todos/"+visitor.taskId()+"/summary",owner,null,404);
        long otherTenant=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned foreign') RETURNING id",Long.class,"N5_"+UUID.randomUUID().toString().substring(0,16));
        var foreign=http.createAndLogin(otherTenant,List.of("todo:read","visitor:read:self","seal:read:self"));
        assertThat(request("GET","/api/todos",foreign,null,200).path("total").asInt()).isZero();
        request("GET","/api/todos/"+seal.taskId()+"/summary",foreign,null,404);
        request("GET","/api/todos",null,null,401);
    }
    @Test void sameNumberedBusinessResourcesKeepDistinctTaskCoordinates() throws Exception {
        var twin=SealDecisionFixture.sameNumberedVisitor(jdbc,tenant,owner.id(),reviewer.id(),seal.id()+900000);
        jdbc.update("UPDATE seal_usage SET id=? WHERE id=?",twin.id(),seal.id());
        jdbc.update("UPDATE workflow_instance SET business_id=? WHERE id=?",twin.id(),seal.instanceId());
        jdbc.update("UPDATE workflow_task SET business_id=? WHERE id=?",twin.id(),seal.taskId());
        var visitorRow=request("GET","/api/todos/"+twin.taskId()+"/summary",reviewer,null,200);
        var sealRow=request("GET","/api/todos/"+seal.taskId()+"/summary",reviewer,null,200);
        assertThat(visitorRow.path("businessType").asText()).isEqualTo("VISITOR_BOOKING");
        assertThat(sealRow.path("businessType").asText()).isEqualTo("SEAL_USAGE");
        assertThat(visitorRow.path("applicationId")).isEqualTo(sealRow.path("applicationId"));
        assertThat(visitorRow.path("id")).isNotEqualTo(sealRow.path("id"));assertConsistent(5);
    }
    @Test void realWorkerAndGatewayDeliverFourBusinessResultsAndConcreteLocalizedSummaries() throws Exception {
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?",tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='todo.query'");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id='todo' AND tool_code='todo.query'",tenant);
        var task=new FrozenReadTaskFixture(jdbc,json,hashing,catalog).create(tenant,reviewer.id(),"todo","todo.query",
                json.createObjectNode().put("businessType","ALL"),FrozenReadTaskFixture.State.QUEUED);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(25)).until(()->{
            worker.poll();return !List.of("QUEUED","RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id()));
        });
        assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo("SUCCEEDED");
        var delivered=request("GET","/api/ai/tasks/"+task.number(),reviewer,null,200).path("steps").get(0);
        assertThat(delivered.path("result").path("items")).hasSize(4);
        assertThat(delivered.path("resultSummary").asText()).contains("Owned visitor","Owned seal document","Frozen generic title")
                .doesNotContain("PRIVATE PHONE","PRIVATE FORM","agent.todo.","agent.result.");
        assertThat(jdbc.queryForObject("SELECT handler_invoked FROM agent_tool_invocation WHERE task_id=? AND decision='ALLOW'",Boolean.class,task.id())).isTrue();
        assertConsistent(4);
    }
    private void revoke(String permission){jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code=?",tenant,reviewer.role(),permission);}
    private JsonNode list(String query)throws Exception{return request("GET","/api/todos"+query,reviewer,null,200);}
    private void assertConsistent(int count)throws Exception{
        var page=list("");assertThat(page.path("total").asInt()).isEqualTo(count);
        var dashboard=request("GET","/api/dashboard/overview",reviewer,null,200);
        for(var metric:dashboard.path("metrics"))if("PENDING_TODOS".equals(metric.path("code").asText()))assertThat(metric.path("value").asInt()).isEqualTo(count);
        assertThat(dashboard.path("todos")).hasSize(count);
        var body=json.createObjectNode().put("from",LocalDate.now().minusDays(1).toString()).put("to",LocalDate.now().toString());
        var csv=request("POST","/api/dashboard/export",reviewer,body.toString(),200);
        assertThat(csv.path("rowCount").asInt()).isEqualTo(count);
        assertThat(page.toString()+dashboard+csv).doesNotContain("PRIVATE PHONE","PRIVATE PURPOSE","PRIVATE REASON","PRIVATE FORM");
    }
    private JsonNode request(String method,String path,AgentHttpTestFixture.Actor actor,String body,int status)throws Exception{
        var response=AgentHttpTestFixture.client().send(http.request(method,path,actor,null,body).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(status);return json.readTree(response.body()).path("data");
    }
}
