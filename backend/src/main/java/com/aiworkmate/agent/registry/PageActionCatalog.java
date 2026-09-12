package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.capability.PageCapabilityCatalog;
import com.aiworkmate.agent.capability.PageCapabilityDefinition;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Code-owned maximum mapping between an OA page and Agent tools.
 * Database policy may only disable one of these bindings; it cannot add a binding.
 */
@Component
public class PageActionCatalog {
    private final PageCapabilityCatalog pageCapabilityCatalog;
    private final ToolCatalog toolCatalog;

    public PageActionCatalog(PageCapabilityCatalog pageCapabilityCatalog, ToolCatalog toolCatalog) {
        this.pageCapabilityCatalog = pageCapabilityCatalog;
        this.toolCatalog = toolCatalog;
    }

    public Set<String> toolCodes(String pageId) {
        return pageCapabilityCatalog.find(pageId)
                .map(PageCapabilityDefinition::tools)
                .orElse(List.of()).stream()
                .map(reference -> reference.toolCode())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean contains(String pageId, String toolCode) {
        return toolCode != null && toolCodes(pageId).contains(toolCode);
    }

    public String canonicalPageId(String pageId) {
        return pageCapabilityCatalog.canonicalPageId(pageId);
    }

    public List<Binding> all() {
        java.util.ArrayList<Binding> bindings = new java.util.ArrayList<>();
        pageCapabilityCatalog.all().forEach(page -> page.tools()
                .forEach(tool -> add(bindings, page.pageId(), tool.toolCode())));
        return bindings.stream()
                .sorted(Comparator.comparing(Binding::pageId).thenComparing(binding -> binding.tool().code()))
                .toList();
    }

    private void add(List<Binding> bindings, String pageId, String toolCode) {
        toolCatalog.find(toolCode).ifPresent(tool -> bindings.add(new Binding(pageId, tool)));
    }

    public record Binding(String pageId, ToolDefinition tool) {}
}
