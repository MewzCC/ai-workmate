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

    @ParameterizedTest
    @ValueSource(strings = {"asset.department.query", "asset.department.query", "asset.department.query", "asset.custodian.query", "asset-owner-by-name"})
    void assetNameLookupsUseTheGovernedDomainWithoutHumanResourcesPermission(String expectedTool) throws Exception {
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=false WHERE tenant_id=?", tenant);
        jdbc.update("DELETE FROM agent_page_action_policy WHERE tenant_id=? AND page_id IN ('asset-ledger','ai-workspace')", tenant);
        var http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var actor = http.createAndLogin(tenant, List.of("route:ai-workspace", "route:asset-ledger", "asset:write",
                "agent:tool:asset.department.query", "agent:tool:asset.custodian.query"));
        assertThat(registry.resolveAllowedTools(access.resolveActiveUser(actor.id()), "ai-workspace"))
                .extracting(definition -> definition.code()).containsExactlyInAnyOrder("asset.department.query", "asset.custodian.query");
        String marker = "lookup-" + UUID.randomUUID();
        long department = jdbc.queryForObject("INSERT INTO department(tenant_id,code,name) VALUES (?,?,?) RETURNING id", Long.class, tenant, marker, marker);
        long person = jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,display_name,password,department_id) VALUES (?,?,?,'unusable synthetic password',?) RETURNING id",
                Long.class, tenant, marker, marker, department);
        String input = "asset.department.query".equals(expectedTool)
                ? "在本租户可选的部门中，按部门名称关键词“" + marker + "”查找部门候选，只查询部门编号和名称。"
                : "asset-owner-by-name".equals(expectedTool)
                    ? "查询资产名称包含“" + marker + "”的资产分别属于哪个部门。不要按部门名称搜索，因为这是资产名称。"
                    : "在部门编号 " + department + " 中按姓名“" + marker + "”查询资产保管候选人，只查询，不领用也不调拨。";
        var request = json.createObjectNode().put("input", input).put("pageId", "ai-workspace");
        request.set("pageContext", json.createObjectNode());
        var planned = AgentHttpTestFixture.client().send(http.request("POST", "/api/ai/tasks/plan", actor,
                UUID.randomUUID().toString(), request.toString()).timeout(Duration.ofSeconds(40)).build(), HttpResponse.BodyHandlers.ofString());
        if ("asset-owner-by-name".equals(expectedTool)) {
            assertThat(planned.statusCode()).isEqualTo(400);
            assertThat(json.readTree(planned.body()).path("errorCode").asText()).isEqualTo("SCHEMA_INVALID");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=?", Long.class, actor.id())).isZero();
            assertThat(MODEL_CALLS).isNotEmpty().allMatch(call -> "NONE".equals(call.failure()));
            assertThat(MODEL_CALLS).anyMatch(call -> call.shape().contains("steps=0"));
            return;
        }
        assertThat(planned.statusCode()).as("asset planning HTTP; code=%s", json.readTree(planned.body()).path("errorCode").asText()).isEqualTo(200);
        var plan = json.readTree(planned.body()).path("data");
        assertThat(plan.path("riskLevel").asText()).isEqualTo("L0");
        assertThat(plan.path("confirmationRequired").asBoolean()).isFalse();
        assertThat(plan.path("steps")).hasSize(1);
        assertThat(plan.path("steps").get(0).path("toolCode").asText()).isEqualTo(expectedTool);
        String task = plan.path("taskId").asText();
        var execute = json.createObjectNode().put("planVersion", plan.path("planVersion").asInt()).put("planHash", plan.path("planHash").asText());
        var accepted = AgentHttpTestFixture.client().send(http.request("POST", "/api/ai/tasks/" + task + "/execute", actor,
                UUID.randomUUID().toString(), execute.toString()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var response = AgentHttpTestFixture.client().send(http.request("GET", "/api/ai/tasks/" + task, actor, null, null).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var detail = json.readTree(response.body()).path("data");
            assertThat(detail.path("status").asText()).isEqualTo("SUCCEEDED");
            var result = detail.path("steps").get(0).path("result");
            assertThat(result.path("total").asInt()).isEqualTo(1);
            assertThat(result.path("items").get(0).path("id").asLong()).isEqualTo("asset.department.query".equals(expectedTool) ? department : person);
            assertThat(result.path("items").get(0).path("name").asText()).isEqualTo(marker);
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE user_id=? AND handler_invoked AND outcome='SUCCEEDED'", Long.class, actor.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND max_risk_level<>'L0'", Long.class, actor.id())).isZero();
        assertThat(MODEL_CALLS).isNotEmpty().allMatch(call -> "NONE".equals(call.failure()));
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
                            result == null ? 0 : result.length(), responseShape(trimmed), "NONE"));
                    return result;
                } catch (RuntimeException failure) {
                    MODEL_CALLS.add(new ModelCall((System.nanoTime() - started) / 1_000_000, 0, "NONE", failure.getClass().getSimpleName()));
                    throw failure;
                }
            };
        }

        private static String responseShape(String raw) {
            if (!raw.startsWith("{")) return raw.startsWith("```") ? "MARKDOWN" : "OTHER";
            try {
                var tree = new ObjectMapper().readTree(raw);
                var steps = tree.path("steps");
                var codes = new java.util.ArrayList<String>();
                for (var step : steps) {
                    String code = step.path("toolCode").asText();
                    codes.add(com.aiworkmate.agent.registry.ToolCode.isSupported(code) ? code : "UNSUPPORTED");
                }
                boolean clarification = tree.path("summary").asText().matches("(?s).*(补充|明确|无法|需要|请提供).*" );
                // Synthetic fixture only: strip every identifier/Latin character before reporting a clarification.
                String reason = steps.isEmpty() ? tree.path("summary").asText().replaceAll("[^\\p{IsHan}，。；：、？！（）\\s]", "").strip() : "";
                if (reason.length() > 120) reason = reason.substring(0, 120);
                return "JSON_OBJECT:steps=" + steps.size() + ":tools=" + codes + ":clarification=" + clarification + ":reason=" + reason;
            } catch (Exception invalid) { return "INVALID_JSON"; }
        }
    }
}
