package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.config.AiProviderRuntime;
import com.aiworkmate.dto.AiConfiguredModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Real model selection evaluation. Synthetic requests only; no tools are executed. */
@EnabledIfSystemProperty(named = "agent.test.live-model", matches = "true")
class ToolSelectionBenchmarkTest {
    @Test void measureSelectionAndArguments() throws Exception {
        var json = new ObjectMapper();
        var codes = Set.of("todo.query", "leave.mine", "notification.mine", "leave.createDraft", "leave.updateDraft",
                "leave.submit", "leave.apply", "approval.task.approve", "approval.task.reject");
        var tools = PlannerCatalogFixture.all(json).stream().filter(t -> codes.contains(t.code())).toList();
        org.assertj.core.api.Assertions.assertThat(tools).hasSize(codes.size());
        var settings = new com.aiworkmate.config.AiRuntimeProperties();
        settings.setApiKey(System.getenv("AI_API_KEY"));
        settings.setBaseUrl(System.getenv("AI_BASE_URL").replaceAll("/+$", "").replaceAll("/v1$", "") + "/v1");
        var configuration = org.mockito.Mockito.mock(com.aiworkmate.mapper.AiConfigurationMapper.class);
        org.mockito.Mockito.when(configuration.load()).thenReturn(null);
        var runtime = new AiProviderRuntime(configuration,
                new com.aiworkmate.config.AiSecretCipher("synthetic-benchmark-secret"), settings, System.getenv("AI_MODEL"));
        var real = new ChatClientPlannerModelClient(new com.aiworkmate.config.AiConfig().chatClient(runtime,
                org.mockito.Mockito.mock(com.aiworkmate.service.AiUsageService.class)), runtime);
        var calls = new AtomicInteger();
        var promptBytes = new AtomicInteger();
        PlannerModelClient measured = new PlannerModelClient() {
            public int inputBudgetBytes() { return real.inputBudgetBytes(); }
            public String complete(String system, String user) {
                calls.incrementAndGet();
                promptBytes.set((system + user).getBytes(StandardCharsets.UTF_8).length);
                return real.complete(system, user);
            }
        };
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
        var cases = Boolean.getBoolean("benchmark.edge-cases") ? List.of(
                new Case("missing_reason_unprompted", "帮我申请明天全天事假", "NEEDS_INPUT"),
                new Case("missing_draft_id", "把我之前的请假草稿提交审批", "NEEDS_INPUT"),
                new Case("missing_version", "提交我已有的请假草稿，申请编号73", "NEEDS_INPUT"),
                new Case("optional_comment", "同意请假审批任务83，版本4", "approval.task.approve")) : List.of(
                new Case("apply", "帮我申请明天全天事假，个人原因", "leave.apply"),
                new Case("draft", "明天请全天事假，个人原因，先存草稿，暂时不要提交", "leave.createDraft"),
                new Case("submit", "提交我已有的请假草稿，申请编号73，版本2", "leave.submit"),
                new Case("mine", "查查我自己提交的请假申请", "leave.mine"),
                new Case("inbox", "看看现在有哪些事情等我审批", "todo.query"),
                new Case("notifications", "看看我的系统通知", "notification.mine"),
                new Case("approve", "同意请假审批任务83，版本4，审批意见原样填写：不太合理", "approval.task.approve"),
                new Case("reject", "驳回请假审批任务83，版本4，驳回意见原样填写：同意", "approval.task.reject"),
                new Case("missing_reason", "帮我申请明天全天事假，原因还没提供，先问我原因", "NEEDS_INPUT"));
        var rows = new ArrayList<String>();
        rows.add("case,iteration,elapsed_ms,model_calls,prompt_bytes,selected,correct");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var planner = new StructuredAgentPlanner(measured, json, new ToolSchemaValidator(), new AgentRuntimeProperties(), executor);
            for (var item : cases) for (int i = 1; i <= Integer.getInteger("benchmark.repeats", 2); i++) {
                int before = calls.get(); long started = System.nanoTime();
                String selected; boolean correct;
                try {
                    var plan = planner.plan(item.input(), "ai-workspace", json.createObjectNode(), tools);
                    selected = plan.requiresClarification() ? "NEEDS_INPUT" : plan.steps().get(0).toolCode();
                    correct = selected.equals(item.expected()) && (plan.requiresClarification() || plan.steps().size() == 1);
                    if (correct && Set.of("apply", "draft").contains(item.id())) {
                        var args = plan.steps().get(0).arguments();
                        correct = args.path("startDate").asText().equals(date) && args.path("endDate").asText().equals(date)
                                && args.path("startPeriod").asText().equals("AM") && args.path("endPeriod").asText().equals("PM")
                                && args.path("reason").asText().equals("个人原因") && args.path("leaveType").asText().equals("PERSONAL");
                    }
                    if (correct && Set.of("approve", "reject", "submit").contains(item.id())) {
                        var args = plan.steps().get(0).arguments();
                        correct = item.id().equals("submit") ? args.path("applicationId").asLong() == 73 && args.path("version").asInt() == 2
                                : args.path("taskId").asLong() == 83 && args.path("version").asInt() == 4
                                && args.path("comment").asText().equals(item.id().equals("approve") ? "不太合理" : "同意");
                    }
                    if (correct && item.id().equals("optional_comment")) {
                        var args = plan.steps().get(0).arguments();
                        correct = args.path("taskId").asLong() == 83 && args.path("version").asInt() == 4;
                    }
                    if (correct && Set.of("mine", "inbox", "notifications").contains(item.id())) {
                        var args = plan.steps().get(0).arguments();
                        var names = new java.util.HashSet<String>(); args.fieldNames().forEachRemaining(names::add);
                        var allowed = item.id().equals("inbox") ? Set.of("page", "size", "businessType", "status") : Set.of("page", "size");
                        correct = allowed.containsAll(names)
                                && (!args.has("page") || args.path("page").asInt() == 1)
                                && (!args.has("size") || args.path("size").asInt() >= 1 && args.path("size").asInt() <= 50)
                                && (!args.has("businessType") || args.path("businessType").asText().equals("ALL"))
                                && (!args.has("status") || args.path("status").asText().equals("PENDING"));
                    }
                } catch (com.aiworkmate.common.BusinessException failure) { selected = failure.getMessageKey(); correct = false; }
                String row = item.id() + "," + i + "," + (System.nanoTime() - started) / 1_000_000
                        + "," + (calls.get() - before) + "," + promptBytes.get() + "," + selected + "," + correct;
                rows.add(row); System.out.println("Selection benchmark: " + row);
            }
        } finally { executor.shutdownNow(); }
        Files.write(Path.of("target/tool-selection-" + System.getProperty("benchmark.label", "run") + ".csv"), rows);
    }
    private record Case(String id, String input, String expected) { }
}
