package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.agent.registry.AgentWriteToolDefinitions;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class PlannerToolDocumentationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void fitsSmallModelUsingTheActualRuntimeBudgetWithoutDroppingToolsOrSchemas() throws Exception {
        var cipher = new com.aiworkmate.config.AiSecretCipher("synthetic-test-secret");
        var mapper = org.mockito.Mockito.mock(com.aiworkmate.mapper.AiConfigurationMapper.class);
        org.mockito.Mockito.when(mapper.load()).thenReturn(java.util.Map.of("model", "small", "base_url", "http://127.0.0.1/v1",
                "encrypted_api_key", cipher.encrypt("synthetic-test-key"), "models",
                com.aiworkmate.config.AiModelSettings.write(List.of(new com.aiworkmate.dto.AiConfiguredModel("small", 16384, 4096, false)))));
        var runtime = new com.aiworkmate.config.AiProviderRuntime(mapper, cipher,
                new com.aiworkmate.config.AiRuntimeProperties(), "small");
        var tools = PlannerCatalogFixture.all(json).stream().filter(t -> java.util.Set.of("todo.query", "leave.mine",
                "notification.mine", "leave.createDraft", "leave.updateDraft", "leave.submit", "leave.apply",
                "approval.task.approve", "approval.task.reject").contains(t.code())).toList();
        var calls = new AtomicInteger();
        PlannerModelClient client = new PlannerModelClient() {
            public int inputBudgetBytes() { return runtime.plannerInputBudgetBytes(); }
            public String complete(String system, String user) {
                calls.incrementAndGet();
                var prepared = runtime.prepare(new org.springframework.ai.chat.prompt.Prompt(List.of(
                        new org.springframework.ai.chat.messages.SystemMessage(system),
                        new org.springframework.ai.chat.messages.UserMessage(user))));
                assertThat(prepared.prompt().getOptions().getModel()).isEqualTo("small");
                try {
                    var entries = json.readTree(user).path("allowedTools");
                    assertThat(entries.size()).isEqualTo(tools.size());
                    for (int i = 0; i < tools.size(); i++)
                        assertThat(entries.get(i).path("argumentsSchema")).isEqualTo(tools.get(i).inputSchema());
                } catch (java.io.IOException invalid) { throw new AssertionError(invalid); }
                return "{\"summary\":\"需要编号\",\"clarification\":\"请提供编号\",\"steps\":[]}";
            }
        };
        new StructuredAgentPlanner(client, json, new ToolSchemaValidator(), new AgentRuntimeProperties(), Runnable::run)
                .plan("提交已有草稿", "ai-workspace", json.createObjectNode(), tools);
        assertThat(calls).hasValue(1);
    }

    @Test void rejectsAnUnrepresentableCatalogBeforeCallingTheModelRatherThanHidingTools() throws Exception {
        var calls = new AtomicInteger();
        PlannerModelClient client = new PlannerModelClient() {
            public int inputBudgetBytes() { return 100; }
            public String complete(String system, String user) { calls.incrementAndGet(); return "{}"; }
        };
        var tool = new AgentWriteToolDefinitions().leaveApplyToolDefinition(json);
        var planner = new StructuredAgentPlanner(client, json, new ToolSchemaValidator(), new AgentRuntimeProperties(), Runnable::run);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> planner.plan("申请请假", "ai-workspace", json.createObjectNode(), List.of(tool)))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("messageKey").isEqualTo("error.aiConfiguration.contextExceeded");
        assertThat(calls).hasValue(0);
    }

    @Test void providesCompleteDocumentationForExactlyTheAllowedToolSet() throws Exception {
        var tools = PlannerCatalogFixture.all(json);
        JsonNode payload = capture(tools);
        assertThat(payload.path("allowedTools").size()).isEqualTo(tools.size());
        for (int i = 0; i < tools.size(); i++) {
            var expected = tools.get(i); var actual = payload.path("allowedTools").get(i);
            assertThat(actual.path("code").asText()).isEqualTo(expected.code());
            assertThat(actual.path("name").asText()).isEqualTo(expected.name());
            assertThat(actual.path("description").asText()).isEqualTo(expected.description());
            assertThat(actual.path("purpose").asText()).isEqualTo(expected.purpose());
            assertThat(actual.path("argumentsSchema")).isEqualTo(expected.inputSchema());
            assertThat(actual.path("returns").isObject()).isTrue();
            assertThat(actual.path("sideEffect").asText()).isEqualTo(expected.sideEffect().name());
            assertThat(actual.path("confirmation").asText()).isEqualTo(expected.confirmationPolicy().name());
            if (actual.has("example")) {
                var validator = new ToolSchemaValidator();
                assertThat(validator.valid(expected.inputSchema(), actual.at("/example/arguments"))).isTrue();
                assertThat(validator.valid(expected.outputSchema(), actual.at("/example/result"))).isTrue();
            }
        }
        System.out.println("Tool documentation coverage: " + tools.size() + " contracts");
    }

    @Test void measuresDocumentationProjectionWithoutAnAdditionalModelCall() throws Exception {
        var tools = PlannerCatalogFixture.all(json);
        var timings = new java.util.ArrayList<Long>();
        int bytes = 0;
        for (int i = 0; i < 60; i++) {
            long started = System.nanoTime();
            var root = json.createObjectNode();
            PlannerToolDocumentation.append(json, root, tools);
            bytes = json.writeValueAsBytes(root).length;
            if (i >= 10) timings.add(System.nanoTime() - started);
        }
        timings.sort(Long::compare);
        long p50 = timings.get(25) / 1_000_000, p95 = timings.get(47) / 1_000_000;
        assertThat(p95).isLessThan(500);
        System.out.println("Tool documentation projection: tools=" + tools.size() + ", bytes=" + bytes
                + ", p50_ms=" + p50 + ", p95_ms=" + p95);
    }

    @Test void documentsParametersOnceAndUsesSchemaValidSyntheticCallAndReturnExamples() throws Exception {
        var factory = new AgentWriteToolDefinitions();
        var tools = List.of(factory.leaveApplyToolDefinition(json), factory.leaveCreateDraftToolDefinition(json),
                factory.leaveSubmitToolDefinition(json));
        var hashes = tools.stream().map(ToolDefinition::schemaHash).toList();
        var payload = capture(tools);
        assertThat(payload.at("/argumentNotes/version").asText()).contains("版本");
        assertThat(payload.at("/argumentNotes/startPeriod").asText()).contains("AM", "PM");
        var validator = new ToolSchemaValidator();
        for (int i = 0; i < tools.size(); i++) {
            var tool = tools.get(i); var example = payload.path("allowedTools").get(i).path("example");
            assertThat(validator.valid(tool.inputSchema(), example.path("arguments"))).isTrue();
            assertThat(validator.valid(tool.outputSchema(), example.path("result"))).isTrue();
            assertThat(tool.schemaHash()).isEqualTo(hashes.get(i));
            assertThat(tool.inputSchema().toString()).doesNotContain("description");
        }
    }

    @Test void neverAddsToolsFromDescriptionsOrUserRequestsAndKeepsResponsesCompact() throws Exception {
        var tool = new AgentWriteToolDefinitions().leaveApplyToolDefinition(json);
        var payload = capture(List.of(tool));
        assertThat(payload.path("allowedTools").size()).isEqualTo(1);
        assertThat(payload.path("allowedTools").get(0).path("code").asText()).isEqualTo("leave.apply");
        assertThat(payload.path("allowedTools").get(0).path("returns").toString().length())
                .isLessThan(tool.outputSchema().toString().length());
        assertThat(payload.path("argumentNotes").has("taskId")).isFalse();
    }

    private JsonNode capture(List<ToolDefinition> tools) throws Exception {
        var captured = new String[1]; var calls = new AtomicInteger();
        PlannerModelClient model = (system, user) -> {
            calls.incrementAndGet(); captured[0] = user;
            assertThat(system).contains("optional", "examples", "Do not invent");
            return "{\"summary\":\"需要编号\",\"clarification\":\"请提供记录编号\",\"steps\":[]}";
        };
        var planner = new StructuredAgentPlanner(model, json, new ToolSchemaValidator(), new AgentRuntimeProperties(), Runnable::run);
        planner.plan("执行用户选择的操作，其他工具不可见", "ai-workspace", json.createObjectNode(), tools);
        assertThat(calls).hasValue(1);
        return json.readTree(captured[0]);
    }
}
