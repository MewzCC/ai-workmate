package com.aiworkmate.integration;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Real authenticated dashboard projection and CSV, using only fixture-owned infrastructure. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardTodoIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT=new AgentIntegrationEnvironment();
    private static final String SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor reviewer,applicant;
    private GenericTaskReadFixture.Submission generic;
    private long tenant;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        AgentBrowserFixture.registerLocal(p,ENVIRONMENT,AgentBrowserFixture.reserveUiPort(),SECRET);
    }
    @BeforeEach void setup() throws Exception {
        tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        http=new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        reviewer=http.createAndLogin(tenant,List.of("dashboard:read","data:export","todo:read","route:todo"));
        applicant=http.createAndLogin(tenant,List.of("dashboard:read"));
        LeaveReadFixture.pending(jdbc,tenant,applicant.id(),reviewer.id());
        generic=pending("Frozen generic title");
    }
    @Test void metricsSummaryAndExportIncludeBothSupportedBusinessesWithoutPayloads() throws Exception {
        var data=overview(reviewer);
        assertThat(pendingCount(data)).isEqualTo(2);
        assertThat(data.path("todos")).hasSize(2);
        assertThat(data.path("todos").findValuesAsText("businessType")).containsExactlyInAnyOrder("LEAVE_APPLICATION","GENERIC_APPROVAL");
        for(var row:data.path("todos"))assertThat(row.path("detailAvailable").asBoolean()).isTrue();
        assertThat(data.toString()).contains("Frozen generic title").doesNotContain("PRIVATE FORM DATA","schema_json","form_schema");
        var csv=export(reviewer,null,200);
        assertThat(csv.path("rowCount").asInt()).isEqualTo(2);
        assertThat(csv.path("content").asText()).contains("GENERIC_APPROVAL","LEAVE_APPLICATION","Frozen generic title").doesNotContain("PRIVATE FORM DATA");
    }
    @Test void genericOnlySummaryDoesNotFallBackToASameNumberedLeaveApplication() throws Exception {
        jdbc.update("UPDATE workflow_task SET status='APPROVED' WHERE tenant_id=? AND assignee_user_id=? AND business_type='LEAVE_APPLICATION'",tenant,reviewer.id());
        jdbc.update("""
                INSERT INTO leave_application(id,tenant_id,applicant_user_id,approver_user_id,leave_type,start_date,
                    start_period,end_date,end_period,duration_half_days,reason,status,submitted_at)
                VALUES (?,?,?,?,'PERSONAL',CURRENT_DATE,'AM',CURRENT_DATE,'PM',2,'PRIVATE SAME-ID LEAVE','PENDING',CURRENT_TIMESTAMP)
                ON CONFLICT(id) DO NOTHING
                """,generic.applicationId(),tenant,applicant.id(),reviewer.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE id=?",Integer.class,generic.applicationId())).isOne();
        var data=overview(reviewer);
        assertThat(pendingCount(data)).isOne();assertThat(data.path("todos")).hasSize(1);
        var row=data.path("todos").get(0);
        assertThat(row.path("taskId").asLong()).isEqualTo(generic.taskId());
        assertThat(row.path("businessId").asLong()).isEqualTo(generic.applicationId());
        assertThat(row.path("businessType").asText()).isEqualTo("GENERIC_APPROVAL");
        assertThat(row.path("title").asText()).isEqualTo("Frozen generic title");
        assertThat(data.toString()).doesNotContain("PRIVATE SAME-ID LEAVE");
        var csv=export(reviewer,null,200);
        assertThat(csv.path("rowCount").asInt()).isOne();
        assertThat(csv.path("content").asText()).contains("GENERIC_APPROVAL").doesNotContain("LEAVE_APPLICATION","PRIVATE SAME-ID LEAVE");
    }
    @Test void frozenTitlesAreNotReplacedByAnEditedDraftAndMissingSnapshotsDisableDetails() throws Exception {
        jdbc.update("UPDATE approval_application SET title='PRIVATE NEW DRAFT',data_json='{\"reason\":\"PRIVATE NEW DATA\"}' WHERE id=?",generic.applicationId());
        assertThat(overview(reviewer).toString()).contains("Frozen generic title").doesNotContain("PRIVATE NEW");
        assertThat(export(reviewer,null,200).toString()).contains("Frozen generic title").doesNotContain("PRIVATE NEW");
        jdbc.update("DELETE FROM approval_execution WHERE instance_id=?",generic.instanceId());
        var data=overview(reviewer);
        assertThat(pendingCount(data)).isEqualTo(2);
        for(var row:data.path("todos"))if(row.path("taskId").asLong()==generic.taskId()){
            assertThat(row.path("detailAvailable").asBoolean()).isFalse();assertThat(row.path("title").asText()).startsWith("GA-");
        }
        assertThat(data.toString()).doesNotContain("PRIVATE NEW");
        assertThat(export(reviewer,null,200).toString()).doesNotContain("PRIVATE NEW");
    }
    @Test void invalidRelationshipsAreExcludedFromAllThreeProjections() throws Exception {
        jdbc.update("UPDATE workflow_instance SET business_id=business_id+100000 WHERE id=?",generic.instanceId());
        assertThat(pendingCount(overview(reviewer))).isOne();
        assertThat(overview(reviewer).path("todos")).hasSize(1);
        assertThat(export(reviewer,null,200).path("rowCount").asInt()).isOne();
    }
    @Test void unsupportedBusinessesDoNotInflateMetricsAndOverdueUsesTheSameScope() throws Exception {
        jdbc.update("UPDATE workflow_task SET due_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE id=?",generic.taskId());
        var data=overview(reviewer);
        assertThat(data.path("metrics").findValuesAsText("code")).contains("OVERDUE_TODOS");
        for(var metric:data.path("metrics"))if("OVERDUE_TODOS".equals(metric.path("code").asText()))assertThat(metric.path("value").asLong()).isOne();
        jdbc.update("UPDATE workflow_task SET business_type='VISITOR_BOOKING' WHERE id=?",generic.taskId());
        jdbc.update("UPDATE workflow_instance SET business_type='VISITOR_BOOKING' WHERE id=?",generic.instanceId());
        assertThat(pendingCount(overview(reviewer))).isOne();
        assertThat(overview(reviewer).path("todos")).hasSize(1);
        assertThat(export(reviewer,null,200).path("rowCount").asInt()).isOne();
    }
    @Test void pendingNodesOfClosedInstancesAreNotActionableTodos() throws Exception {
        jdbc.update("UPDATE workflow_instance SET status='COMPLETED' WHERE id=?",generic.instanceId());
        assertThat(pendingCount(overview(reviewer))).isOne();
        assertThat(overview(reviewer).path("todos")).hasSize(1);
        assertThat(export(reviewer,null,200).path("rowCount").asInt()).isOne();
    }
    @Test void summaryIsAnEightItemWindowAndKeywordUsesFrozenTitleWithSafeCsv() throws Exception {
        for(int i=0;i<10;i++)pending("Other frozen "+i);
        pending("=SUM(A1:A2)");
        assertThat(pendingCount(overview(reviewer))).isEqualTo(13);
        assertThat(overview(reviewer).path("todos")).hasSize(8);
        assertThat(export(reviewer,null,200).path("rowCount").asInt()).isEqualTo(13);
        var csv=export(reviewer,"SUM(A1:A2)",200);
        assertThat(csv.path("rowCount").asInt()).isOne();
        assertThat(csv.path("content").asText()).contains("\"'=SUM(A1:A2)\"");
    }
    @Test void otherAssigneesTenantsAndRevokedUsersCannotReadOrExportTheseTasks() throws Exception {
        assertThat(pendingCount(overview(applicant))).isZero();
        export(applicant,null,403);
        long foreign=jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Owned foreign tenant') RETURNING id",Long.class,"DASH_"+UUID.randomUUID().toString().substring(0,16));
        var outsider=http.createAndLogin(foreign,List.of("dashboard:read","data:export"));
        assertThat(pendingCount(overview(outsider))).isZero();assertThat(export(outsider,null,200).path("rowCount").asInt()).isZero();
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='data:export'",tenant,reviewer.role());
        export(reviewer,null,403);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='dashboard:read'",tenant,reviewer.role());
        request("GET","/api/dashboard/overview",reviewer,null,403);
        request("GET","/api/dashboard/overview",null,null,401);
    }
    private GenericTaskReadFixture.Submission pending(String title){
        return GenericTaskReadFixture.pending(jdbc,json,tenant,applicant.id(),reviewer.id(),title,"{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}","{\"reason\":\"PRIVATE FORM DATA\"}");
    }
    private JsonNode overview(AgentHttpTestFixture.Actor actor) throws Exception{return request("GET","/api/dashboard/overview",actor,null,200);}
    private long pendingCount(JsonNode data){for(var metric:data.path("metrics"))if("PENDING_TODOS".equals(metric.path("code").asText()))return metric.path("value").asLong();throw new AssertionError("Missing pending metric");}
    private JsonNode export(AgentHttpTestFixture.Actor actor,String keyword,int status)throws Exception{
        var body=json.createObjectNode().put("from",LocalDate.now().minusDays(1).toString()).put("to",LocalDate.now().toString());
        if(keyword!=null)body.put("keyword",keyword);
        return request("POST","/api/dashboard/export",actor,body.toString(),status);
    }
    private JsonNode request(String method,String path,AgentHttpTestFixture.Actor actor,String body,int status)throws Exception{
        var response=AgentHttpTestFixture.client().send(http.request(method,path,actor,null,body).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
}
