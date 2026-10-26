package com.aiworkmate.integration;

import com.aiworkmate.agent.planner.ChatClientPlannerModelClient;
import com.aiworkmate.agent.planner.PlannerModelClient;
import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.service.UserAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Explicit external-model acceptance. IT suffix excludes this from the offline default test suite. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AgentLiveModelHttpIT.ModelObservation.class)
class AgentLiveModelHttpIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final ConcurrentLinkedQueue<ModelCall> MODEL_CALLS = new ConcurrentLinkedQueue<>();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private ToolRegistry registry;
    @Autowired private UserAccessService access;
    @Autowired private AiRuntimeProperties ai;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        LiveModelTestConfiguration.register(properties, ENVIRONMENT, "oa_model_it");
    }

    @BeforeEach void resetObservations() { MODEL_CALLS.clear(); }

    @ParameterizedTest(name = "real conversation/business intent {index}: {0}")
    @CsvSource(value = {
            "CHAT|刚才我让你记住的短语是什么？请原样复述。",
            "CHAT|再复述我最开始让你记住的短语。",
            "CHAT|请重复本次对话中我上条消息的原文，不访问其他数据。",
            "OPERATION|刚才提交的请假申请现在是什么状态？",
            "OPERATION|请查询我的待办并复述每条待办的内容。",
            "OPERATION|记住我的长期系统偏好并保存到账号设置。"
    }, delimiter = '|')
    void conversationRecallIsNotMisroutedToBusinessPlanning(String expected, String input) throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var http = new AgentHttpTestFixture(jdbc,json,passwords,port,"zh-CN");
        var actor = http.createAndLogin(tenant,List.of("route:ai-workspace"));
        var response = AgentHttpTestFixture.client().send(http.request("POST","/api/ai/intents",actor,null,
                json.createObjectNode().put("input",input).toString()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("live intent HTTP status").isEqualTo(200);
        assertThat(json.readTree(response.body()).path("data").path("intent").asText()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=?",Long.class,actor.id())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"notification.mine", "todo.query"})
    void aNaturalLanguageRequestSelectsTheRightToolAndReturnsAnUnseenDatabaseValue(String expectedTool) throws Exception {
        assertThat(ai.configured()).isTrue();
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code IN ('notification.mine','todo.query')");
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id IN ('messages','todo','ai-workspace')", tenant);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var actor = http.createAndLogin(tenant, List.of("route:ai-workspace", "route:messages", "notification:read:self", "agent:tool:notification.mine",
                "route:todo", "todo:read", "agent:tool:todo.query"));
        assertThat(registry.resolveAllowedTools(access.resolveActiveUser(actor.id()), "ai-workspace"))
                .extracting(definition -> definition.code()).containsExactlyInAnyOrder("notification.mine", "todo.query");
        var applicant = http.createAndLogin(tenant, List.of());
        String applicantName = jdbc.queryForObject("SELECT username FROM app_user WHERE id=?", String.class, applicant.id());
        long applicationId = LeaveReadFixture.pending(jdbc, tenant, applicant.id(), actor.id());
        LeaveReadFixture.pending(jdbc, tenant, applicant.id(), applicant.id());
        String marker = "live-notice-" + UUID.randomUUID();
        jdbc.update("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM',?,'synthetic acceptance data')", tenant, actor.id(), marker);
        jdbc.update("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM','other owner','not visible')", tenant, applicant.id());
        String input = "todo.query".equals(expectedTool) ? "查一下我的待办。" : "请帮我查询我的通知。";
        var intent = AgentHttpTestFixture.client().send(http.request("POST", "/api/ai/intents", actor, null,
                json.createObjectNode().put("input", input).toString()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(intent.statusCode()).as("live intent HTTP status").isEqualTo(200);
        assertThat(json.readTree(intent.body()).path("data").path("intent").asText()).isEqualTo("OPERATION");

        var request = json.createObjectNode().put("input", input).put("pageId", "ai-workspace");
        request.set("pageContext", json.createObjectNode());
        var planned = AgentHttpTestFixture.client().send(http.request("POST", "/api/ai/tasks/plan", actor,
                UUID.randomUUID().toString(), request.toString()).timeout(Duration.ofSeconds(40)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(planned.statusCode()).as("live planning HTTP status").isEqualTo(200);
        var plan = json.readTree(planned.body()).path("data");
        assertThat(plan.path("status").asText()).isEqualTo("PLAN_READY");
        assertThat(plan.path("riskLevel").asText()).isEqualTo("L0");
        assertThat(plan.path("confirmationRequired").asBoolean()).isFalse();
        assertThat(plan.path("steps")).hasSize(1);
        assertThat(plan.path("steps").get(0).path("toolCode").asText()).isEqualTo(expectedTool);
        String number = plan.path("taskId").asText();
        assertThat(number).matches("[a-f0-9-]{36}");
        long taskId = jdbc.queryForObject("SELECT id FROM agent_task WHERE task_no=? AND tenant_id=? AND user_id=?", Long.class, number, tenant, actor.id());
        assertThat(jdbc.queryForObject("SELECT result IS NULL FROM agent_task_step WHERE task_id=?", Boolean.class, taskId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=?", Long.class, taskId)).isZero();

        var executeBody = json.createObjectNode().put("planVersion", plan.path("planVersion").asInt()).put("planHash", plan.path("planHash").asText());
        var accepted = AgentHttpTestFixture.client().send(http.request("POST", "/api/ai/tasks/" + number + "/execute", actor,
                UUID.randomUUID().toString(), executeBody.toString()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var detail = AgentHttpTestFixture.client().send(http.request("GET", "/api/ai/tasks/" + number, actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(detail.statusCode()).isEqualTo(200);
            var data = json.readTree(detail.body()).path("data");
            assertThat(data.path("status").asText()).isEqualTo("SUCCEEDED");
            var result = data.path("steps").get(0).path("result");
            assertThat(result.path("total").asLong()).isEqualTo(1);
            if ("notification.mine".equals(expectedTool)) {
                assertThat(result.path("items").get(0).path("title").asText()).isEqualTo(marker);
            } else {
                assertThat(result.path("items").get(0).path("applicationId").asLong()).isEqualTo(applicationId);
                assertThat(result.path("items").get(0).path("applicantName").asText()).isEqualTo(applicantName);
            }
            assertThat(data.path("steps").get(0).path("resultSummary").asText()).isNotBlank();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked AND outcome='SUCCEEDED'", Long.class, taskId)).isEqualTo(1);
        assertThat(MODEL_CALLS).hasSizeBetween(2, 3).allMatch(call -> "NONE".equals(call.failure()));
    }

    @AfterEach void reportMetadataOnly() {
        MODEL_CALLS.forEach(call -> System.out.println("Live model metadata: " + call));
    }

    record ModelCall(long durationMs, int responseCharacters, String shape, String failure) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ModelObservation {
        @Bean @Primary PlannerModelClient observedPlannerModelClient(ChatClientPlannerModelClient delegate) {
            return (system, input) -> {
                long started = System.nanoTime();
                try {
                    String result = delegate.complete(system, input);
                    String trimmed = result == null ? "" : result.stripLeading();
                    MODEL_CALLS.add(new ModelCall((System.nanoTime() - started) / 1_000_000,
                            result == null ? 0 : result.length(), trimmed.startsWith("{") ? "JSON_OBJECT" : trimmed.startsWith("```") ? "MARKDOWN" : "OTHER", "NONE"));
                    return result;
                } catch (RuntimeException failure) {
                    MODEL_CALLS.add(new ModelCall((System.nanoTime() - started) / 1_000_000, 0, "NONE", failure.getClass().getSimpleName()));
                    throw failure;
                }
            };
        }
    }
}
