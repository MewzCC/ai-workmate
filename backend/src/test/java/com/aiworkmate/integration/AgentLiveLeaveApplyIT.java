package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Explicit real-model L2 acceptance, confined to one disposable DB, Redis and synthetic Cookie identity. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentLiveLeaveApplyIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT=new AgentIntegrationEnvironment();
    @Override protected AgentIntegrationEnvironment environment(){return ENVIRONMENT;}
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        LiveModelTestConfiguration.register(p,ENVIRONMENT,"oa_live_leave_it");
        p.add("agent.write-tools-enabled",()->"true"); // Only this verified disposable application context.
    }
    @Test void oneSentenceProducesOneConfirmedAtomicLeaveApplication() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo(ENVIRONMENT.schema());
        long tenant=jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'",Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?",tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='leave.apply'");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id='ai-workspace' AND tool_code='leave.apply'",tenant);
        var http=new AgentHttpTestFixture(jdbc,json,passwords,port,"zh-CN");
        var actor=http.createAndLogin(tenant,List.of("route:ai-workspace","route:leave-application","route:my-applications",
                "leave:create","leave:read:self","agent:tool:leave.apply"));
        long approver=LeaveWriteFixture.linkedApprover(jdbc,tenant,actor.id());
        String reason="live-leave-"+UUID.randomUUID(), date=LocalDate.now().plusDays(3).toString();
        String input="请帮我直接提交"+date+"上午至当天下午的事假申请，原因填写："+reason+"，使用系统为我配置的审批人。";
        var classified=AgentHttpTestFixture.client().send(http.request("POST","/api/ai/intents",actor,null,
                json.createObjectNode().put("input",input).toString()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(classified.statusCode()).isEqualTo(200);
        assertThat(json.readTree(classified.body()).path("data").path("intent").asText()).isEqualTo("OPERATION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=?",Long.class,actor.id())).isZero();
        var body=json.createObjectNode().put("input",input).put("pageId","ai-workspace");
        body.putObject("pageContext");
        var planned=AgentHttpTestFixture.client().send(http.request("POST","/api/ai/tasks/plan",actor,UUID.randomUUID().toString(),body.toString())
                .timeout(Duration.ofSeconds(40)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(planned.statusCode()).as("real model plan status").isEqualTo(200);
        var plan=json.readTree(planned.body()).path("data");
        assertThat(plan.path("status").asText()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(plan.path("riskLevel").asText()).isEqualTo("L2");
        assertThat(plan.path("confirmationRequired").asBoolean()).isTrue();
        assertThat(plan.path("steps")).hasSize(1);
        assertThat(plan.path("steps").get(0).path("toolCode").asText()).isEqualTo("leave.apply");
        var arguments=plan.path("steps").get(0).path("arguments");
        assertThat(arguments.path("leaveType").asText()).isEqualTo("PERSONAL");
        assertThat(arguments.path("startDate").asText()).isEqualTo(date);
        assertThat(arguments.path("endDate").asText()).isEqualTo(date);
        assertThat(arguments.path("startPeriod").asText()).isEqualTo("AM");
        assertThat(arguments.path("endPeriod").asText()).isEqualTo("PM");
        assertThat(arguments.path("reason").asText()).isEqualTo(reason);
        String path="/api/ai/tasks/"+plan.path("taskId").asText(), key=UUID.randomUUID().toString();
        var execute=json.createObjectNode().put("planVersion",plan.path("planVersion").asInt()).put("planHash",plan.path("planHash").asText());
        var missing=AgentHttpTestFixture.client().send(http.request("POST",path+"/execute",actor,key,execute.toString()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?",Long.class,actor.id())).isZero();
        var confirmed=AgentHttpTestFixture.client().send(http.request("POST",path+"/confirmation-token",actor,null,execute.toString()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(confirmed.statusCode()).isEqualTo(200);
        execute.put("confirmationToken",json.readTree(confirmed.body()).path("data").path("confirmationToken").asText());
        var accepted=AgentHttpTestFixture.client().send(http.request("POST",path+"/execute",actor,key,execute.toString()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->{
            var response=AgentHttpTestFixture.client().send(http.request("GET",path,actor,null,null).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var task=json.readTree(response.body()).path("data");
            assertThat(task.path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(task.path("steps").get(0).path("result").path("status").asText()).isEqualTo("PENDING");
            assertThat(task.path("steps").get(0).path("resultSummary").asText()).contains("已提交请假申请","审批待办","等待审批");
        });
        var finished=AgentHttpTestFixture.client().send(http.request("GET",path,actor,null,null).build(),HttpResponse.BodyHandlers.ofString());
        var receipt=json.readTree(finished.body()).path("data").path("steps").get(0).path("result");
        long application=receipt.path("applicationId").asLong(), approvalTask=receipt.path("approvalTaskId").asLong();
        assertThat(application).isPositive();
        assertThat(approvalTask).isPositive();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM leave_application WHERE id=? AND tenant_id=? AND applicant_user_id=?
                  AND approver_user_id=? AND reason=? AND status='PENDING' AND duration_half_days=2
                  AND leave_type='PERSONAL' AND start_date=?::date AND end_date=?::date AND start_period='AM' AND end_period='PM'
                """,Long.class,application,tenant,actor.id(),approver,reason,date,date)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM workflow_instance i JOIN leave_application a ON a.workflow_instance_id=i.id AND a.tenant_id=i.tenant_id
                WHERE a.id=? AND a.tenant_id=? AND a.applicant_user_id=? AND i.business_type='LEAVE_APPLICATION'
                  AND i.business_id=a.id AND i.status='RUNNING'
                """,Long.class,application,tenant,actor.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM workflow_task t JOIN leave_application a ON a.workflow_instance_id=t.instance_id AND a.tenant_id=t.tenant_id
                WHERE t.id=? AND t.tenant_id=? AND t.business_type='LEAVE_APPLICATION' AND t.business_id=a.id
                  AND a.id=? AND a.applicant_user_id=? AND t.assignee_user_id=? AND t.status='PENDING'
                """,Long.class,approvalTask,tenant,application,actor.id(),approver)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE tenant_id=? AND actor_user_id=? AND resource_type='LEAVE_APPLICATION' AND resource_id=? AND action='AGENT_APPLY' AND result='SUCCESS'",Long.class,tenant,actor.id(),Long.toString(application))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked AND outcome='SUCCEEDED'",Long.class,actor.id())).isEqualTo(1);
        System.out.println("Live leave acceptance: real model, one L2 step, actual confirmation and one pending application passed");
    }
}
