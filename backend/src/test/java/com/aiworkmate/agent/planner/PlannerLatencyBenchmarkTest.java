package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.agent.registry.AgentWriteToolDefinitions;
import com.aiworkmate.config.AiProviderRuntime;
import com.aiworkmate.dto.AiConfiguredModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit opt-in, synthetic input only. Never executes tools or writes business data. */
@EnabledIfSystemProperty(named = "agent.test.live-model", matches = "true")
class PlannerLatencyBenchmarkTest {
    @Test void measureClarificationPlanning() throws Exception {
        var json = new ObjectMapper();
        var model = AiProviderRuntime.createModel(System.getenv("AI_BASE_URL").replaceAll("/+$", "")
                .replaceAll("/v1$", "") + "/v1", System.getenv("AI_API_KEY"),
                AiConfiguredModel.defaults(System.getenv("AI_MODEL")));
        var client = new ChatClientPlannerModelClient(ChatClient.create(model));
        var tool = new AgentWriteToolDefinitions().leaveApplyToolDefinition(json);
        var broadTools = new ArrayList<com.aiworkmate.agent.registry.ToolDefinition>();
        try (var sources = Files.list(Path.of("src/main/java/com/aiworkmate/agent/registry"))) {
            for (Path source : sources.filter(p -> p.getFileName().toString().endsWith("Definitions.java")).sorted().toList()) {
                var type = Class.forName("com.aiworkmate.agent.registry." + source.getFileName().toString().replace(".java", ""));
                Object definitions = type.getConstructor().newInstance();
                for (var method : type.getMethods()) {
                    if (method.getReturnType().equals(com.aiworkmate.agent.registry.ToolDefinition.class)
                            && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{ObjectMapper.class}))
                        broadTools.add((com.aiworkmate.agent.registry.ToolDefinition) method.invoke(definitions, json));
                }
            }
        }
        System.out.println("Planner benchmark tool counts: focused=1, broad=" + broadTools.size());
        var executor = Executors.newFixedThreadPool(2);
        var rows = new ArrayList<String>();
        rows.add("case,iteration,total_ms,first_summary_ms,status,correct_date");
        try {
            var planner = new StructuredAgentPlanner(client, json, new ToolSchemaValidator(),
                    new AgentRuntimeProperties(), executor);
            int repeats = Integer.getInteger("benchmark.repeats", 3);
            for (String scenario : List.of("explicit_date", "tomorrow_reply", "broad_tomorrow_reply")) {
                for (int i = 1; i <= repeats; i++) {
                    long started = System.nanoTime();
                    var first = new AtomicLong(-1);
                    String status = "OK";
                    boolean correct = false;
                    String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
                    try {
                        var candidate = PlannerReplyStream.observe(text -> {
                            if (!text.isBlank()) first.compareAndSet(-1, (System.nanoTime() - started) / 1_000_000);
                        }, () -> scenario.equals("explicit_date")
                                ? planner.plan("请帮我申请" + date + "全天事假，个人原因", "ai-workspace", json.createObjectNode(), List.of(tool))
                                : planner.planContinuation("帮我请个假到明天,事假,个人原因",
                                        "请问请假从哪天开始、到哪天结束？请提供具体日期。", "明天早上9:00到明天晚上18:00",
                                        "ai-workspace", json.createObjectNode(), scenario.startsWith("broad") ? broadTools : List.of(tool)));
                        correct = !candidate.requiresClarification() && candidate.steps().size() == 1
                                && candidate.steps().get(0).toolCode().equals("leave.apply")
                                && candidate.steps().get(0).arguments().path("startDate").asText().equals(date)
                                && candidate.steps().get(0).arguments().path("endDate").asText().equals(date);
                        if (candidate.requiresClarification()) status = "NEEDS_INPUT";
                    } catch (com.aiworkmate.common.BusinessException error) {
                        status = error.getMessageKey();
                    }
                    String row = scenario + "," + i + "," + (System.nanoTime() - started) / 1_000_000
                            + "," + first.get() + "," + status + "," + correct;
                    rows.add(row);
                    System.out.println("Planner benchmark: " + row);
                }
            }
        } finally { executor.shutdownNow(); }
        Path output = Path.of("target", "planner-benchmark-" + System.getProperty("benchmark.label", "run") + ".csv");
        Files.write(output, rows);
    }
}
