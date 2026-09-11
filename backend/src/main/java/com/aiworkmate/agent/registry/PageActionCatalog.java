package com.aiworkmate.agent.registry;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Code-owned maximum mapping between an OA page and Agent tools.
 * Database policy may only disable one of these bindings; it cannot add a binding.
 */
@Component
public class PageActionCatalog {
    private static final Map<String, Set<String>> PAGE_TOOLS = Map.of(
            "todo-list", Set.of("todo.query"),
            "my-applications", Set.of("leave.mine", "leave.createDraft", "leave.submit", "leave.apply"),
            "knowledge-base", Set.of("knowledge.search"),
            "message-center", Set.of("notification.mine"),
            "dashboard", Set.of("todo.query", "notification.mine")
    );

    private final ToolCatalog toolCatalog;

    public PageActionCatalog(ToolCatalog toolCatalog) {
        this.toolCatalog = toolCatalog;
    }

    public Set<String> toolCodes(String pageId) {
        if ("ai-workspace".equals(pageId)) {
            return toolCatalog.all().stream().map(ToolDefinition::code)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return PAGE_TOOLS.getOrDefault(pageId, Set.of());
    }

    public boolean contains(String pageId, String toolCode) {
        return toolCode != null && toolCodes(pageId).contains(toolCode);
    }

    public List<Binding> all() {
        java.util.ArrayList<Binding> bindings = new java.util.ArrayList<>();
        PAGE_TOOLS.forEach((pageId, tools) -> tools.forEach(tool -> add(bindings, pageId, tool)));
        toolCatalog.all().forEach(tool -> bindings.add(new Binding("ai-workspace", tool)));
        return bindings.stream()
                .sorted(Comparator.comparing(Binding::pageId).thenComparing(binding -> binding.tool().code()))
                .toList();
    }

    private void add(List<Binding> bindings, String pageId, String toolCode) {
        toolCatalog.find(toolCode).ifPresent(tool -> bindings.add(new Binding(pageId, tool)));
    }

    public record Binding(String pageId, ToolDefinition tool) {}
}
