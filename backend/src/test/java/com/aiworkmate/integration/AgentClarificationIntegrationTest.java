package com.aiworkmate.integration;

import com.aiworkmate.agent.planner.AgentPlanner;
import com.aiworkmate.agent.planner.PlannerCandidate;
import com.aiworkmate.agent.task.AgentTaskApiService;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** Deterministic planner boundary, real authentication/state/confirmation/Gateway/domain transactions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import(ManualDecisionWorkerConfiguration.class)
class AgentClarificationIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    private static final String QUESTION = "请提供驳回理由。";
    @Override protected AgentIntegrationEnvironment environment() { return ENV; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired AgentTaskWorker worker;
    @Autowired AgentTaskApiService taskApi;
    @MockBean AgentPlanner planner;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor actor, other;
    private GenericTaskReadFixture.Submission generic;
    private long tenant;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        AgentBrowserFixture.registerLocal(p, ENV, AgentBrowserFixture.reserveUiPort(), SECRET);
        p.add("agent.enabled", () -> "true"); p.add("agent.planning-enabled", () -> "true");
        p.add("agent.execution-enabled", () -> "true"); p.add("agent.write-tools-enabled", () -> "true");
        p.add("spring.ai.openai.api-key", () -> "synthetic-no-model-call");
        p.add("agent.worker.poll-delay-ms", () -> "3600000");
        p.add("agent.worker.heartbeat-delay-ms", () -> "3600000");
        p.add("agent.confirmation.expiry-delay-ms", () -> "3600000");
    }
    @BeforeEach void setup() throws Exception {
        when(planner.planContinuation(anyString(), anyString(), anyString(), anyString(), any(), anyList()))
                .thenAnswer(call -> planner.plan(call.getArgument(0, String.class) + "\n\n" + call.getArgument(2, String.class),
                        call.getArgument(3), call.getArgument(4), call.getArgument(5)));
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "en-US");
        var permissions = List.of("route:ai-workspace", "route:todo", "approval:act", "todo:read",
                "agent:tool:approval.genericTask.reject");
        actor = http.createAndLogin(tenant, permissions); other = http.createAndLogin(tenant, permissions);
        generic = GenericTaskReadFixture.pending(jdbc, json, tenant, other.id(), actor.id(), "Owned clarification",
                "{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}", "{\"reason\":\"private frozen value\"}");
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code='approval.genericTask.reject'");
        when(planner.plan(anyString(), anyString(), any(), anyList())).thenAnswer(invocation -> {
            String input = invocation.getArgument(0);
            if (!input.contains("资料不完整")) return new PlannerCandidate("需要理由", List.of(), QUESTION);
            return new PlannerCandidate("Reject one task", List.of(new PlannerCandidate.Step("approval.genericTask.reject",
                    json.createObjectNode().put("taskId", generic.taskId()).put("version", 0).put("comment", "资料不完整"))));
        });
    }
    private HttpResponse<String> send(String method, String path, AgentHttpTestFixture.Actor who, String key, String body) throws Exception {
        return AgentHttpTestFixture.client().send(http.request(method, path, who, key, body).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> plan(AgentHttpTestFixture.Actor who, String input, String source, Long conversation, String key) throws Exception {
        var body = json.createObjectNode().put("input", input).put("pageId", "ai-workspace");
        if (source != null) body.put("clarificationTaskId", source);
        if (conversation != null) body.put("conversationId", conversation);
        return send("POST", "/api/ai/tasks/plan", who, key, body.toString());
    }
    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as("owned HTTP error=%s", json.readTree(response.body()).path("errorCode").asText()).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }
    private JsonNode question(Long conversation) throws Exception {
        var question = data(plan(actor, "请驳回当前任务", null, conversation, UUID.randomUUID().toString()));
        assertThat(question.path("status").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(question.path("steps")).isEmpty();
        assertThat(question.path("summary").asText()).isEqualTo(QUESTION);
        assertThat(question.path("confirmationRequired").asBoolean()).isFalse();
        return question;
    }
    private String path(JsonNode task) { return "/api/ai/tasks/" + task.path("taskId").asText(); }
    private com.fasterxml.jackson.databind.node.ObjectNode coordinates(JsonNode task) {
        return json.createObjectNode().put("planVersion", task.path("planVersion").asInt()).put("planHash", task.path("planHash").asText());
    }
    private void unchanged() {
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_task WHERE id=?", String.class, generic.taskId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked", Long.class, actor.id())).isZero();
    }

    @Test void questionCannotExecuteAndAnswerCreatesANewConfirmedSingleWrite() throws Exception {
        var question = question(null);
        assertThat(send("POST", path(question) + "/execute", actor, UUID.randomUUID().toString(), coordinates(question).toString()).statusCode()).isEqualTo(409);
        assertThat(send("POST", path(question) + "/confirmation-token", actor, null, coordinates(question).toString()).statusCode()).isEqualTo(409);
        unchanged();
        String key = UUID.randomUUID().toString();
        var next = data(plan(actor, "资料不完整", question.path("taskId").asText(), null, key));
        assertThat(next.path("status").asText()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(next.path("taskId").asText()).isNotEqualTo(question.path("taskId").asText());
        assertThat(next.path("planHash").asText()).isNotEqualTo(question.path("planHash").asText());
        assertThat(data(plan(actor, "资料不完整", question.path("taskId").asText(), null, key))).isEqualTo(next);
        assertThat(data(send("GET", path(question), actor, null, null)).path("status").asText()).isEqualTo("CANCELLED");
        unchanged();
        var execution = coordinates(next);
        assertThat(send("POST", path(next) + "/execute", actor, UUID.randomUUID().toString(), execution.toString()).statusCode()).isEqualTo(409);
        var credential = data(send("POST", path(next) + "/confirmation-token", actor, null, execution.toString()));
        execution.put("confirmationToken", credential.path("confirmationToken").asText());
        assertThat(send("POST", path(next) + "/execute", actor, UUID.randomUUID().toString(), execution.toString()).statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            worker.poll();
            assertThat(data(send("GET", path(next), actor, null, null)).path("status").asText()).isEqualTo("SUCCEEDED");
        });
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, generic.applicationId())).isEqualTo("REJECTED");
        assertThat(send("POST", path(next) + "/execute", actor, UUID.randomUUID().toString(), execution.toString()).statusCode()).isNotEqualTo(202);
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked", Long.class, actor.id())).isOne();
    }

    @Test void concurrentAnswersCanConsumeTheQuestionOnlyOnce() throws Exception {
        String source = question(null).path("taskId").asText();
        var first = CompletableFuture.supplyAsync(() -> answer(source));
        var second = CompletableFuture.supplyAsync(() -> answer(source));
        var statuses = List.of(first.get().statusCode(), second.get().statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND status='WAITING_CONFIRMATION'", Long.class, actor.id())).isOne();
        unchanged();
    }
    private HttpResponse<String> answer(String source) {
        try { return plan(actor, "资料不完整", source, null, UUID.randomUUID().toString()); }
        catch (Exception failure) { throw new CompletionException(failure); }
    }

    @Test void cancelledExpiredRevokedAndOtherOwnersCannotContinue() throws Exception {
        var question = question(null); String source = question.path("taskId").asText();
        assertThat(plan(other, "资料不完整", source, null, UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        long foreignTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?, 'Owned foreign tenant') RETURNING id", Long.class,
                "CLAR_" + UUID.randomUUID().toString().replace("-", ""));
        var outsider = http.createAndLogin(foreignTenant, List.of("route:ai-workspace", "route:todo", "todo:read",
                "approval:act", "agent:tool:approval.genericTask.reject"));
        jdbc.update("INSERT INTO agent_tenant_policy(tenant_id,enabled,write_tools_enabled) VALUES (?,true,true) ON CONFLICT(tenant_id) DO UPDATE SET enabled=true,write_tools_enabled=true", foreignTenant);
        assertThat(plan(outsider, "资料不完整", source, null, UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
        var wrongPage = json.createObjectNode().put("input", "资料不完整").put("pageId", "todo").put("clarificationTaskId", source);
        assertThat(send("POST", "/api/ai/tasks/plan", actor, UUID.randomUUID().toString(), wrongPage.toString()).statusCode()).isEqualTo(403);
        jdbc.update("DELETE FROM rbac_role_permission WHERE tenant_id=? AND role_code=? AND permission_code='approval:act'", tenant, actor.role());
        assertThat(plan(actor, "资料不完整", source, null, UUID.randomUUID().toString()).statusCode()).isGreaterThanOrEqualTo(400);
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,'approval:act')", tenant, actor.role());
        data(send("POST", path(question) + "/cancel", actor, null, null));
        assertThat(plan(actor, "资料不完整", source, null, UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
        var expired = question(null); String expiredNo = expired.path("taskId").asText();
        jdbc.update("UPDATE agent_task SET confirmation_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE task_no=?", expiredNo);
        taskApi.expireConfirmations();
        assertThat(data(send("GET", path(expired), actor, null, null)).path("status").asText()).isEqualTo("EXPIRED");
        assertThat(plan(actor, "资料不完整", expiredNo, null, UUID.randomUUID().toString()).statusCode()).isEqualTo(409);
        unchanged();
    }

    @Test void conversationPersistsTheQuestionAndRawAnswerAndRejectsCrossConversationReuse() throws Exception {
        long conversation = jdbc.queryForObject("INSERT INTO conversation(tenant_id,user_id,title,model) VALUES (?,?,'Owned clarification','synthetic') RETURNING id", Long.class, tenant, actor.id());
        long otherConversation = jdbc.queryForObject("INSERT INTO conversation(tenant_id,user_id,title,model) VALUES (?,?,'Another conversation','synthetic') RETURNING id", Long.class, tenant, actor.id());
        var question = question(conversation); String source = question.path("taskId").asText();
        assertThat(jdbc.queryForList("SELECT content FROM message WHERE conversation_id=? ORDER BY id", String.class, conversation)).containsExactly("请驳回当前任务", QUESTION);
        assertThat(plan(actor, "资料不完整", source, otherConversation, UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
        var next = data(plan(actor, "资料不完整", source, conversation, UUID.randomUUID().toString()));
        assertThat(next.path("status").asText()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(jdbc.queryForList("SELECT content FROM message WHERE conversation_id=? ORDER BY id", String.class, conversation)).containsExactly("请驳回当前任务", QUESTION, "资料不完整");
        var orphaned = question(otherConversation);
        jdbc.update("DELETE FROM conversation WHERE id=?", otherConversation);
        assertThat(plan(actor, "资料不完整", orphaned.path("taskId").asText(), null, UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
        unchanged();
    }

    @Test void roundAndUtf8LimitsRollBackWithoutConsumingTheLastQuestion() throws Exception {
        var first = question(null);
        String firstNo = first.path("taskId").asText();
        assertThat(plan(actor, "界".repeat(1360), firstNo, null, UUID.randomUUID().toString()).statusCode()).isEqualTo(400);
        assertThat(data(send("GET", path(first), actor, null, null)).path("status").asText()).isEqualTo("NEEDS_INPUT");
        var second = data(plan(actor, "暂时没有理由", firstNo, null, UUID.randomUUID().toString()));
        var third = data(plan(actor, "仍未决定", second.path("taskId").asText(), null, UUID.randomUUID().toString()));
        assertThat(third.path("status").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(plan(actor, "再问一次", third.path("taskId").asText(), null, UUID.randomUUID().toString()).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=?", Long.class, actor.id())).isEqualTo(3);
        assertThat(data(send("GET", path(third), actor, null, null)).path("status").asText()).isEqualTo("NEEDS_INPUT");
        var completed = data(plan(actor, "资料不完整", third.path("taskId").asText(), null, UUID.randomUUID().toString()));
        assertThat(completed.path("status").asText()).isEqualTo("WAITING_CONFIRMATION");
        unchanged();
    }
}
