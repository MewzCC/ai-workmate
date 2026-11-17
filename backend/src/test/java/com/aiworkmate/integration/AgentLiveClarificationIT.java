package com.aiworkmate.integration;

import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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

/** Actual model asks for missing information, then creates a new confirmed atomic command. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import(ManualDecisionWorkerConfiguration.class)
class AgentLiveClarificationIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV = new AgentIntegrationEnvironment();
    @Override protected AgentIntegrationEnvironment environment() { return ENV; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired AgentTaskWorker worker;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        LiveModelTestConfiguration.register(p, ENV, "oa_live_clarification_it");
        p.add("agent.write-tools-enabled", () -> "true");
    }
    @Test void aRealModelQuestionAndAnswerStillNeedANewConfirmationBeforeOneWrite() throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var actor = http.createAndLogin(tenant, List.of("route:ai-workspace", "route:leave-application",
                "route:my-applications", "leave:create", "leave:read:self", "agent:tool:leave.apply"));
        LeaveWriteFixture.linkedApprover(jdbc, tenant, actor.id());
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='leave.apply'");
        long conversation = jdbc.queryForObject("INSERT INTO conversation(tenant_id,user_id,title,model) VALUES (?,?,'Owned live question','synthetic') RETURNING id", Long.class, tenant, actor.id());
        String date = LocalDate.now().plusDays(3).toString();
        String initial = "我想提交" + date + "上午至当天下午的事假申请，使用系统为我配置的审批人。";
        var request = json.createObjectNode().put("input", initial).put("pageId", "ai-workspace").put("conversationId", conversation);
        var response = AgentHttpTestFixture.client().send(http.request("POST", "/api/ai/tasks/plan", actor,
                UUID.randomUUID().toString(), request.toString()).timeout(Duration.ofSeconds(40)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var question = json.readTree(response.body()).path("data");
        assertThat(question.path("status").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(question.path("summary").asText()).containsAnyOf("原因", "理由", "事由");
        assertThat(question.path("steps")).isEmpty();
        noWrite(actor.id());
        String reason = "live-clarification-" + UUID.randomUUID();
        String reply = "请假原因填写：" + reason;
        request.put("input", reply).put("clarificationTaskId", question.path("taskId").asText());
        response = AgentHttpTestFixture.client().send(http.request("POST", "/api/ai/tasks/plan", actor,
                UUID.randomUUID().toString(), request.toString()).timeout(Duration.ofSeconds(40)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var plan = json.readTree(response.body()).path("data");
        assertThat(plan.path("status").asText()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(plan.path("steps")).hasSize(1);
        assertThat(plan.at("/steps/0/toolCode").asText()).isEqualTo("leave.apply");
        assertThat(plan.at("/steps/0/arguments/reason").asText()).contains(reason);
        assertThat(plan.at("/steps/0/arguments/startDate").asText()).isEqualTo(date);
        assertThat(plan.path("planHash").asText()).isNotEqualTo(question.path("planHash").asText());
        String path = "/api/ai/tasks/" + plan.path("taskId").asText();
        var body = json.createObjectNode().put("planVersion", plan.path("planVersion").asInt()).put("planHash", plan.path("planHash").asText());
        assertThat(AgentHttpTestFixture.client().send(http.request("POST", path + "/execute", actor,
                UUID.randomUUID().toString(), body.toString()).build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(409);
        noWrite(actor.id());
        var token = AgentHttpTestFixture.client().send(http.request("POST", path + "/confirmation-token", actor,
                null, body.toString()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(token.statusCode()).isEqualTo(200);
        body.put("confirmationToken", json.readTree(token.body()).at("/data/confirmationToken").asText());
        assertThat(AgentHttpTestFixture.client().send(http.request("POST", path + "/execute", actor,
                UUID.randomUUID().toString(), body.toString()).build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            worker.poll();
            assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE task_no=?", String.class, plan.path("taskId").asText())).isEqualTo("SUCCEEDED");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=? AND reason LIKE ? AND status='PENDING'", Long.class, actor.id(), "%" + reason + "%")).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked", Long.class, actor.id())).isOne();
        var persisted = AgentHttpTestFixture.client().send(http.request("POST", "/api/conversations/" + conversation + "/agent-results/" + plan.path("taskId").asText(), actor,
                null, null).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(persisted.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message WHERE conversation_id=?", Long.class, conversation)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT content FROM message WHERE conversation_id=? AND role='user' ORDER BY id", String.class, conversation)).containsExactly(initial, reply);
        System.out.println("Live clarification acceptance: HTTP question, raw reply, fresh confirmation and one persisted business result passed");
    }
    private void noWrite(long userId) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_application WHERE applicant_user_id=?", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked", Long.class, userId)).isZero();
    }
}
