package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.capability.PageToolAccess;
import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.tool.internal.ToolHandler;
import com.aiworkmate.oa.page.OaPage;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Fails application startup when code-owned tool contracts drift apart. */
@Component
public class AgentToolContractGate {
    public AgentToolContractGate(
            List<ToolDefinition> definitions,
            List<ToolHandler> handlers,
            PageCapabilityCatalog pageCapabilities
    ) {
        Map<String, ToolDefinition> definitionsByCode = indexDefinitions(definitions);
        require(definitionsByCode.keySet().equals(ToolCode.codes()),
                "Tool definitions must exactly match the code-owned tool list");

        Map<HandlerKey, ToolHandler> handlersByKey = indexHandlers(handlers);
        Set<HandlerKey> expectedHandlers = definitions.stream()
                .map(definition -> new HandlerKey(definition.code(), definition.handlerVersion()))
                .collect(Collectors.toUnmodifiableSet());
        require(handlersByKey.keySet().equals(expectedHandlers),
                "Tool handlers must exactly match definition codes and versions");

        Map<String, String> expectedPages = Arrays.stream(OaPage.values())
                .collect(Collectors.toUnmodifiableMap(OaPage::routeKey, OaPage::componentKey));
        Map<String, String> actualPages = pageCapabilities.all().stream()
                .collect(Collectors.toUnmodifiableMap(
                        page -> page.pageId(),
                        page -> page.componentKey()
                ));
        require(actualPages.equals(expectedPages),
                "Agent page capabilities must exactly match the code-owned OA page manifest");

        Set<String> pageToolCodes = pageCapabilities.all().stream()
                .flatMap(page -> page.tools().stream())
                .map(reference -> {
                    ToolDefinition definition = definitionsByCode.get(reference.toolCode());
                    require(definition != null, "Page references a tool without a definition");
                    SideEffect expected = reference.access() == PageToolAccess.READ
                            ? SideEffect.NONE : SideEffect.SINGLE_WRITE;
                    require(definition.sideEffect() == expected,
                            "Page tool access does not match the tool side effect");
                    return reference.toolCode();
                })
                .collect(Collectors.toUnmodifiableSet());
        require(pageToolCodes.equals(ToolCode.codes()),
                "Every code-owned tool must be bound to at least one page");
    }

    private Map<String, ToolDefinition> indexDefinitions(List<ToolDefinition> definitions) {
        Map<String, ToolDefinition> indexed = new LinkedHashMap<>();
        for (ToolDefinition definition : definitions) {
            ToolCode.fromCode(definition.code());
            require(indexed.putIfAbsent(definition.code(), definition) == null,
                    "Duplicate tool definition in contract gate");
        }
        return Map.copyOf(indexed);
    }

    private Map<HandlerKey, ToolHandler> indexHandlers(List<ToolHandler> handlers) {
        Map<HandlerKey, ToolHandler> indexed = new LinkedHashMap<>();
        for (ToolHandler handler : handlers) {
            ToolCode.fromCode(handler.toolCode());
            require(handler.handlerVersion() != null && !handler.handlerVersion().isBlank(),
                    "Tool handler version is required");
            HandlerKey key = new HandlerKey(handler.toolCode(), handler.handlerVersion());
            require(indexed.putIfAbsent(key, handler) == null,
                    "Duplicate tool handler in contract gate");
        }
        return Map.copyOf(indexed);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record HandlerKey(String toolCode, String version) { }
}
