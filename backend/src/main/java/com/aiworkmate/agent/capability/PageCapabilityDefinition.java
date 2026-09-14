package com.aiworkmate.agent.capability;

import com.aiworkmate.agent.registry.OwnershipPolicy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Code-owned maximum capability manifest for one OA page.
 * Runtime and tenant configuration may only narrow this definition.
 */
public record PageCapabilityDefinition(
        String pageId,
        String componentKey,
        int version,
        Set<PageUiCommand> uiCommands,
        List<PageToolReference> tools,
        Set<String> requiredPermissions,
        OwnershipPolicy dataScopePolicy,
        PageContextSchema contextSchema
) {
    private static final Pattern PAGE_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,59}$");
    private static final Pattern COMPONENT_KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,59}$");
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("^route:[a-z][a-z0-9-]{1,59}$");

    public PageCapabilityDefinition {
        if (pageId == null || !PAGE_ID_PATTERN.matcher(pageId).matches()) {
            throw new IllegalArgumentException("Invalid capability pageId");
        }
        if (componentKey == null || !COMPONENT_KEY_PATTERN.matcher(componentKey).matches()) {
            throw new IllegalArgumentException("Invalid capability componentKey");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Capability version must be positive");
        }
        uiCommands = Set.copyOf(uiCommands == null ? Set.of() : uiCommands);
        tools = List.copyOf(tools == null ? List.of() : tools);
        requiredPermissions = Set.copyOf(requiredPermissions == null ? Set.of() : requiredPermissions);
        if (requiredPermissions.isEmpty()
                || requiredPermissions.stream().anyMatch(permission -> permission == null
                || !PERMISSION_PATTERN.matcher(permission).matches())) {
            throw new IllegalArgumentException("Page capabilities require explicit route permissions");
        }
        if (dataScopePolicy == null || contextSchema == null) {
            throw new IllegalArgumentException("Page data scope and context schema are required");
        }
        Set<String> toolCodes = new HashSet<>();
        for (PageToolReference tool : tools) {
            if (tool == null || !toolCodes.add(tool.toolCode())) {
                throw new IllegalArgumentException("Duplicate or null page tool reference");
            }
        }
    }

    public List<PageToolReference> readTools() {
        return tools.stream().filter(tool -> tool.access() == PageToolAccess.READ).toList();
    }

    public List<PageToolReference> writeTools() {
        return tools.stream().filter(tool -> tool.access() == PageToolAccess.SINGLE_WRITE).toList();
    }
}
