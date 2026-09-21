package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.capability.PageCapabilityDefinition;
import com.aiworkmate.agent.registry.ConfirmationPolicy;
import com.aiworkmate.agent.registry.OwnershipPolicy;
import com.aiworkmate.agent.registry.PermissionMode;
import com.aiworkmate.agent.registry.RetryPolicy;
import com.aiworkmate.agent.registry.RiskLevel;
import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.tool.internal.ToolExecutionTemplate;
import com.aiworkmate.agent.tool.internal.ToolHandler;
import com.aiworkmate.agent.tool.internal.TrustedToolContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolContractGateTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PageCapabilityCatalog pages = new PageCapabilityCatalog();

    @Test
    void acceptsOneVersionMatchedDefinitionAndHandlerForEveryPageTool() {
        List<ToolDefinition> definitions = definitions();

        assertThatCode(() -> new AgentToolContractGate(definitions, handlers(definitions), pages))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrVersionDriftedHandler() {
        List<ToolDefinition> definitions = definitions();
        List<ToolHandler> missing = handlers(definitions).stream().skip(1).toList();
        assertThatThrownBy(() -> new AgentToolContractGate(definitions, missing, pages))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("handlers");

        List<ToolHandler> drifted = definitions.stream()
                .map(definition -> handler(definition.code(), ToolCode.TODO_QUERY.code().equals(definition.code())
                        ? "2.0.0" : definition.handlerVersion(), templateFor(definition)))
                .toList();
        assertThatThrownBy(() -> new AgentToolContractGate(definitions, drifted, pages))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("versions");
    }

    @Test
    void rejectsDefinitionMissingFromTheCodeOwnedList() {
        List<ToolDefinition> incomplete = definitions().stream().skip(1).toList();

        assertThatThrownBy(() -> new AgentToolContractGate(incomplete, handlers(incomplete), pages))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("definitions");
    }

    @Test
    void rejectsPageCapabilityMissingFromTheCodeOwnedManifest() {
        List<ToolDefinition> definitions = definitions();
        PageCapabilityCatalog incompletePages = new PageCapabilityCatalog() {
            @Override
            public Collection<PageCapabilityDefinition> all() {
                return pages.all().stream().skip(1).toList();
            }
        };

        assertThatThrownBy(() -> new AgentToolContractGate(
                definitions, handlers(definitions), incompletePages))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("page capabilities");
    }

    @Test
    void rejectsHandlerTemplateThatWeakensDefinitionExecutionPolicy() {
        List<ToolDefinition> definitions = definitions();
        List<ToolHandler> unsafe = definitions.stream()
                .map(definition -> handler(definition.code(), definition.handlerVersion(),
                        ToolCode.NOTIFICATION_MARK_READ.code().equals(definition.code())
                                ? ToolExecutionTemplate.DIRECT_WRITE
                                : templateFor(definition)))
                .toList();

        assertThatThrownBy(() -> new AgentToolContractGate(definitions, unsafe, pages))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotent execution template");
    }

    private List<ToolDefinition> definitions() {
        return Arrays.stream(ToolCode.values()).map(this::definition).toList();
    }

    private ToolDefinition definition(ToolCode code) {
        boolean write = code.isWrite();
        JsonNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false)
                .set("properties", objectMapper.createObjectNode());
        boolean idempotent = code == ToolCode.NOTIFICATION_MARK_READ;
        return ToolDefinition.create(code, code.code(), "Contract test definition", "Contract test purpose",
                "1.0.0", schema, schema, write ? RiskLevel.L1 : RiskLevel.L0, Set.of("tool:test"),
                PermissionMode.ALL, OwnershipPolicy.SELF,
                write ? (idempotent ? RetryPolicy.BUSINESS_IDEMPOTENT : RetryPolicy.NEVER)
                        : RetryPolicy.READ_ONLY_SAFE,
                write ? SideEffect.SINGLE_WRITE : SideEffect.NONE,
                write ? ConfirmationPolicy.EXPLICIT : ConfirmationPolicy.NONE,
                1, 1024, 1000, "TEST_AUDIT");
    }

    private List<ToolHandler> handlers(List<ToolDefinition> definitions) {
        return definitions.stream().map(definition ->
                handler(definition.code(), definition.handlerVersion(), templateFor(definition))).toList();
    }

    private ToolExecutionTemplate templateFor(ToolDefinition definition) {
        return definition.sideEffect() == SideEffect.NONE
                ? ToolExecutionTemplate.READ_ONLY
                : (definition.retryPolicy() == RetryPolicy.BUSINESS_IDEMPOTENT
                    ? ToolExecutionTemplate.NATURALLY_IDEMPOTENT_WRITE
                    : ToolExecutionTemplate.DIRECT_WRITE);
    }

    private ToolHandler handler(String code, String version, ToolExecutionTemplate template) {
        return new ToolHandler() {
            @Override public String toolCode() { return code; }
            @Override public String handlerVersion() { return version; }
            @Override public ToolExecutionTemplate executionTemplate() { return template; }
            @Override public JsonNode execute(TrustedToolContext context, JsonNode arguments) { return arguments; }
        };
    }
}
