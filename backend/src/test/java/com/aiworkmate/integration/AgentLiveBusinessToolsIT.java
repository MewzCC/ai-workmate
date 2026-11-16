package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolRegistry;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.aiworkmate.service.UserAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Opt-in real-model coverage of the N1–N11 additions, using disposable business data only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import(ManualDecisionWorkerConfiguration.class)
class AgentLiveBusinessToolsIT extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENV = new AgentIntegrationEnvironment();
    private static final List<String> CODES = List.of("approval.genericTask.reject", "visitor.approve", "visitor.reject",
            "seal.approve", "seal.reject", "approval.genericTask.transfer", "approval.genericTask.cc",
            "approval.genericTask.addSign", "approval.genericTask.participants.query",
            "hr.employee.documents.query", "seal.documents.query");
    record Scenario(String code, String purpose) {
        boolean read() { return code.endsWith(".query"); }
        @Override public String toString() { return code + (purpose.isEmpty() ? "" : "/" + purpose); }
    }
    static Stream<Scenario> scenarios() {
        return Stream.concat(CODES.stream().filter(code -> !code.equals("approval.genericTask.addSign")
                        && !code.equals("approval.genericTask.participants.query")).map(code -> new Scenario(code, "")),
                Stream.of(new Scenario("approval.genericTask.addSign", "PRE"), new Scenario("approval.genericTask.addSign", "POST"),
                        new Scenario("approval.genericTask.participants.query", "TRANSFER"),
                        new Scenario("approval.genericTask.participants.query", "CC"),
                        new Scenario("approval.genericTask.participants.query", "ADD_SIGN")));
    }
    @Override protected AgentIntegrationEnvironment environment() { return ENV; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ToolRegistry registry;
    @Autowired UserAccessService access;
    @Autowired AgentTaskWorker worker;
    private AgentHttpTestFixture http;
    private AgentHttpTestFixture.Actor actor, recipient;
    private GenericTaskReadFixture.Submission generic;
    private VisitorDecisionFixture.Booking visitor;
    private SealDecisionFixture.Usage seal;
    private long tenant, ownUsage;
    private String reason, name, employeeDocument, sealDocument;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        LiveModelTestConfiguration.register(p, ENV, "oa_live_business_it");
        p.add("agent.write-tools-enabled", () -> "true"); // Disposable test context, never a runtime release.
    }
    @BeforeEach void setup() throws Exception {
        tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        http = new AgentHttpTestFixture(jdbc, json, passwords, port, "zh-CN");
        var permissions = new ArrayList<>(List.of("route:ai-workspace", "route:todo", "route:visitor-booking",
                "route:seal-usage", "route:employee-files", "approval:act", "todo:read", "hr:read", "seal:register"));
        CODES.forEach(code -> permissions.add("agent:tool:" + code));
        actor = http.createAndLogin(tenant, permissions);
        var applicant = http.createAndLogin(tenant, List.of());
        recipient = http.createAndLogin(tenant, List.of("approval:act", "todo:read", "route:todo"));
        name = jdbc.queryForObject("SELECT username FROM app_user WHERE id=?", String.class, recipient.id());
        reason = "本次流程办理需要由指定人员复核（验收标识：" + UUID.randomUUID() + "）";
        jdbc.update("UPDATE agent_tenant_policy SET enabled=true,write_tools_enabled=true WHERE tenant_id=?", tenant);
        for (String code : CODES) jdbc.update("UPDATE agent_tool SET enabled=true WHERE tenant_id IS NULL AND code=?", code);
        generic = GenericTaskReadFixture.pending(jdbc, json, tenant, applicant.id(), actor.id(), "Owned live generic",
                "{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}", "{\"reason\":\"PRIVATE FORM VALUE\"}");
        visitor = VisitorDecisionFixture.pending(jdbc, tenant, applicant.id(), actor.id());
        seal = SealDecisionFixture.pending(jdbc, tenant, applicant.id(), actor.id());
        ownUsage = SealDecisionFixture.pending(jdbc, tenant, actor.id(), recipient.id()).id();
        employeeDocument = "employee-" + UUID.randomUUID() + ".txt";
        sealDocument = "seal-" + UUID.randomUUID() + ".txt";
        jdbc.update("INSERT INTO employee_document(tenant_id,employee_user_id,document_type,display_name,storage_key,mime_type,file_size,uploaded_by_user_id) VALUES (?,?,'PROFILE',?,?,'text/plain',123,?)",
                tenant, actor.id(), employeeDocument, "private-unread-key/" + UUID.randomUUID(), actor.id());
        jdbc.update("INSERT INTO seal_usage_document(tenant_id,seal_usage_id,display_name,storage_key,mime_type,file_size,uploaded_by_user_id) VALUES (?,?,?,?,'text/plain',456,?)",
                tenant, ownUsage, sealDocument, "private-unread-key/" + UUID.randomUUID(), actor.id());
        assertThat(registry.resolveAllowedTools(access.resolveActiveUser(actor.id()), "ai-workspace"))
                .extracting(definition -> definition.code()).containsExactlyInAnyOrderElementsOf(CODES);
    }

    private long taskId(Scenario s) {
        return s.code().startsWith("visitor.") ? visitor.taskId() : s.code().startsWith("seal.") ? seal.taskId() : generic.taskId();
    }
    private int version(Scenario s) { return s.code().startsWith("visitor.") || s.code().startsWith("seal.") ? 2 : 0; }
    private String prompt(Scenario s) {
        return switch (s.code()) {
            case "hr.employee.documents.query" -> "查询员工编号 " + actor.id() + " 的档案附件元数据列表，不读取文件内容。";
            case "seal.documents.query" -> "查询用印申请编号 " + ownUsage + " 的留档附件元数据列表，不读取文件内容。";
            case "approval.genericTask.participants.query" -> "查询通用审批任务编号 " + generic.taskId()
                    + " 的" + switch (s.purpose()) { case "TRANSFER" -> "转交"; case "CC" -> "抄送"; default -> "加签"; }
                    + "候选人员，按用户名关键词“" + name + "”筛选，只查询，不修改任务。";
            default -> {
                String business = s.code().startsWith("visitor.") ? "访客预约" : s.code().startsWith("seal.") ? "用印" : "通用";
                String action = s.code().endsWith(".reject") ? "驳回" : s.code().endsWith(".approve") ? "通过"
                        : s.code().endsWith(".transfer") ? "转交" : s.code().endsWith(".cc") ? "抄送" : "加签";
                String target = s.code().endsWith(".transfer") || s.code().endsWith(".cc") || s.code().endsWith(".addSign")
                        ? "，接收人员编号 " + recipient.id() : "";
                String mode = s.code().endsWith(".addSign") ? "，采用" + (s.purpose().equals("PRE") ? "前加签 PRE" : "后加签 POST") : "";
                yield "请" + action + business + "审批任务，任务编号 " + taskId(s) + "，任务版本 " + version(s)
                        + target + mode + "，理由/意见原文：“" + reason + "”。这些是已核实的任务坐标，只处理这一个任务。";
            }
        };
    }
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return AgentHttpTestFixture.client().send(http.request(method, path, actor,
                method.equals("POST") ? UUID.randomUUID().toString() : null, body)
                .timeout(Duration.ofSeconds(40)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void unchanged() {
        for (long task : List.of(generic.taskId(), visitor.taskId(), seal.taskId()))
            assertThat(jdbc.queryForObject("SELECT status FROM workflow_task WHERE id=?", String.class, task)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workflow_action_log WHERE task_id IN (?,?,?)", Long.class,
                generic.taskId(), visitor.taskId(), seal.taskId())).isZero();
    }

    @ParameterizedTest(name = "real model selects and executes {0}") @MethodSource("scenarios")
    void realModelSelectsTheExactNewToolAndExecutesThroughTheGateway(Scenario scenario) throws Exception {
        verifyScenario(scenario, "");
    }

    @ParameterizedTest(name = "real model clarifies and then executes {0}") @MethodSource("scenarios")
    void realModelRequestsMissingCoordinatesBeforePlanningAndExecuting(Scenario scenario) throws Exception {
        verifyScenario(scenario, "coordinates");
    }

    static Stream<Scenario> reasonScenarios() {
        return scenarios().filter(s -> s.code().endsWith(".reject") || s.code().endsWith(".transfer")
                || s.code().endsWith(".cc") || s.code().endsWith(".addSign"));
    }

    static Stream<Scenario> approvalScenarios() {
        return scenarios().filter(s -> s.code().endsWith(".approve"));
    }

    @ParameterizedTest(name = "explicit approval is not reversed by a critical comment for {0}") @MethodSource("approvalScenarios")
    void explicitApprovalIsNotReplacedWithRejection(Scenario scenario) throws Exception {
        reason = "业务资料缺少必要签字，需要指定人员复核（验收标识：" + UUID.randomUUID() + "）";
        verifyScenario(scenario, "");
    }

    @ParameterizedTest(name = "real model asks for the required reason before {0}") @MethodSource("reasonScenarios")
    void realModelDoesNotInventAMissingReason(Scenario scenario) throws Exception {
        verifyScenario(scenario, "reason");
    }

    private String missingCoordinates(Scenario scenario) {
        return prompt(scenario)
                .replace("这些是已核实的任务坐标，", "")
                .replace("任务编号 " + taskId(scenario) + "，任务版本 " + version(scenario), "尚未指定任务编号和任务版本")
                .replace("任务编号 " + generic.taskId(), "任务（尚未指定任务编号）")
                .replace("员工编号 " + actor.id(), "员工（尚未指定员工编号）")
                .replace("用印申请编号 " + ownUsage, "用印申请（尚未指定申请编号）");
    }

    private String answer(Scenario scenario, String missing) {
        if (missing.equals("reason")) return reason;
        return switch (scenario.code()) {
            case "hr.employee.documents.query" -> "员工编号 " + actor.id();
            case "seal.documents.query" -> "用印申请编号 " + ownUsage;
            case "approval.genericTask.participants.query" -> "通用审批任务编号 " + generic.taskId();
            default -> "任务编号 " + taskId(scenario) + "，任务版本 " + version(scenario);
        };
    }

    private void verifyScenario(Scenario scenario, String missing) throws Exception {
        boolean clarify = !missing.isEmpty();
        String input = missing.equals("coordinates") ? missingCoordinates(scenario) : missing.equals("reason")
                ? prompt(scenario).replace("，理由/意见原文：“" + reason + "”。", "。理由尚未提供。") : prompt(scenario);
        var request = json.createObjectNode().put("input", input)
                .put("pageId", "ai-workspace");
        request.putObject("pageContext");
        var response = send("POST", "/api/ai/tasks/plan", request.toString());
        assertThat(response.statusCode()).as("real model plan HTTP status").isEqualTo(200);
        var plan = json.readTree(response.body()).path("data");
        if (clarify) {
            assertThat(plan.path("status").asText()).isEqualTo("NEEDS_INPUT");
            assertThat(plan.path("riskLevel").asText()).isEqualTo("L0");
            assertThat(plan.path("confirmationRequired").asBoolean()).isFalse();
            assertThat(plan.path("steps")).isEmpty();
            assertThat(plan.path("summary").asText()).isNotBlank();
            String question = plan.path("taskId").asText();
            String questionHash = plan.path("planHash").asText();
            var proof = json.createObjectNode().put("planVersion", plan.path("planVersion").asInt())
                    .put("planHash", questionHash);
            for (String action : List.of("execute", "confirmation-token"))
                assertThat(send("POST", "/api/ai/tasks/" + question + "/" + action, proof.toString()).statusCode())
                        .as("question is not executable").isEqualTo(409);
            unchanged();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation i JOIN agent_task t ON t.id=i.task_id WHERE t.task_no=?", Long.class, question)).isZero();
            request.put("input", answer(scenario, missing)).put("clarificationTaskId", question);
            response = send("POST", "/api/ai/tasks/plan", request.toString());
            assertThat(response.statusCode()).as("complete reply HTTP status").isEqualTo(200);
            plan = json.readTree(response.body()).path("data");
            assertThat(plan.path("taskId").asText()).isNotEqualTo(question);
            assertThat(plan.path("planHash").asText()).isNotEqualTo(questionHash);
            assertThat(jdbc.queryForObject("SELECT status FROM agent_task WHERE task_no=?", String.class, question)).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation i JOIN agent_task t ON t.id=i.task_id WHERE t.task_no=?", Long.class, question)).isZero();
        }
        assertThat(plan.path("status").asText()).as("complete synthetic request still asks: %s", plan.path("summary").asText())
                .isEqualTo(scenario.read() ? "PLAN_READY" : "WAITING_CONFIRMATION");
        assertThat(plan.path("riskLevel").asText()).isEqualTo(scenario.read() ? "L0" : "L2");
        assertThat(plan.path("confirmationRequired").asBoolean()).isEqualTo(!scenario.read());
        int plannedSteps = plan.path("steps").size();
        assertThat(plannedSteps).isBetween(1, 2);
        var step = plan.path("steps").get(0);
        if (plannedSteps == 2) {
            assertThat(scenario.code()).isIn("approval.genericTask.transfer", "approval.genericTask.cc", "approval.genericTask.addSign");
            assertThat(step.path("toolCode").asText()).isEqualTo("approval.genericTask.participants.query");
            assertThat(step.at("/arguments/taskId").asLong()).isEqualTo(generic.taskId());
            assertThat(step.at("/arguments/purpose").asText()).isEqualTo(scenario.code().endsWith(".transfer") ? "TRANSFER"
                    : scenario.code().endsWith(".cc") ? "CC" : "ADD_SIGN");
            step = plan.path("steps").get(1);
        }
        assertThat(step.path("toolCode").asText()).isEqualTo(scenario.code());
        var args = step.path("arguments");
        if (scenario.code().equals("approval.genericTask.participants.query")) {
            assertThat(args.path("taskId").asLong()).isEqualTo(generic.taskId());
            assertThat(args.path("purpose").asText()).isEqualTo(scenario.purpose());
        }
        if (!scenario.read()) {
            assertThat(args.path("taskId").asLong()).isEqualTo(taskId(scenario));
            assertThat(args.path("version").asInt(-1)).isEqualTo(version(scenario));
            if (!scenario.code().endsWith(".approve"))
                assertThat(args.path(scenario.code().endsWith(".reject") ? "comment" : "reason").asText()).isEqualTo(reason);
            if (scenario.code().endsWith(".addSign")) assertThat(args.path("mode").asText()).isEqualTo(scenario.purpose());
        }
        String path = "/api/ai/tasks/" + plan.path("taskId").asText();
        long id = jdbc.queryForObject("SELECT id FROM agent_task WHERE task_no=? AND user_id=?", Long.class,
                plan.path("taskId").asText(), actor.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=?", Long.class, id)).isZero();
        unchanged();
        var execute = json.createObjectNode().put("planVersion", plan.path("planVersion").asInt()).put("planHash", plan.path("planHash").asText());
        if (!scenario.read()) {
            assertThat(send("POST", path + "/execute", execute.toString()).statusCode()).isEqualTo(409);
            unchanged();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Long.class, id)).isZero();
            var token = send("POST", path + "/confirmation-token", execute.toString());
            assertThat(token.statusCode()).isEqualTo(200);
            execute.put("confirmationToken", json.readTree(token.body()).at("/data/confirmationToken").asText());
        }
        assertThat(send("POST", path + "/execute", execute.toString()).statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(25)).until(() -> {
            worker.poll();
            return !List.of("QUEUED", "RUNNING").contains(jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?", String.class, id));
        });
        var detail = send("GET", path, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        var task = json.readTree(detail.body()).path("data");
        assertThat(task.path("status").asText()).as("real business error=%s", task.path("errorCode").asText()).isEqualTo("SUCCEEDED");
        var result = task.path("steps").get(plannedSteps - 1).path("result");
        assertThat(task.path("steps").get(plannedSteps - 1).path("resultSummary").asText()).isNotBlank();
        assertThat(result.toString()).doesNotContain("private-unread-key", "PRIVATE FORM VALUE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked AND outcome='SUCCEEDED'", Long.class, id)).isEqualTo((long) plannedSteps);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND tool_code=? AND handler_invoked", Long.class, id, scenario.code())).isOne();
        verifyResult(scenario, result);
        if (!scenario.read()) {
            assertThat(send("POST", path + "/execute", execute.toString()).statusCode()).isNotEqualTo(202);
            worker.poll();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked", Long.class, id)).isEqualTo((long) plannedSteps);
        }
        System.out.println("Live business acceptance: " + scenario + (clarify ? " clarification-" + missing + "," : "") + " actual result, isolation and one invocation per step passed");
    }

    private void verifyResult(Scenario s, JsonNode result) {
        if (s.code().equals("hr.employee.documents.query") || s.code().equals("seal.documents.query")) {
            String expected = s.code().startsWith("hr.") ? employeeDocument : sealDocument;
            assertThat(result.path("items")).hasSize(1);
            assertThat(result.at("/items/0/displayName").asText()).isEqualTo(expected);
            unchanged();
        } else if (s.code().equals("approval.genericTask.participants.query")) {
            assertThat(result.path("items").findValues("id")).extracting(JsonNode::asLong).contains(recipient.id());
            unchanged();
        } else if (s.code().startsWith("visitor.") || s.code().startsWith("seal.")) {
            String status = s.code().endsWith(".approve") ? "APPROVED" : "REJECTED";
            String table = s.code().startsWith("visitor.") ? "visitor_booking" : "seal_usage";
            long resource = s.code().startsWith("visitor.") ? visitor.id() : seal.id();
            assertThat(result.path("resourceId").asLong()).isEqualTo(resource);
            assertThat(result.path("resourceStatus").asText()).isEqualTo(status);
            assertThat(jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=?", String.class, resource)).isEqualTo(status);
        } else if (s.code().endsWith(".reject")) {
            assertThat(result.path("applicationId").asLong()).isEqualTo(generic.applicationId());
            assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, generic.applicationId())).isEqualTo("REJECTED");
        } else {
            assertThat(result.path("targetUserId").asLong()).isEqualTo(recipient.id());
            assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, generic.applicationId())).isEqualTo("PENDING");
            if (s.code().endsWith(".addSign")) {
                assertThat(result.path("mode").asText()).isEqualTo(s.purpose());
                assertThat(result.path("addedTaskId").asLong()).isPositive();
            }
        }
    }
}
