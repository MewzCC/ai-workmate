package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.registry.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Loads code-owned contracts only; no application context, handlers, database or network. */
final class PlannerCatalogFixture {
    static List<ToolDefinition> all(ObjectMapper json) throws Exception {
        var tools = new ArrayList<ToolDefinition>();
        try (var sources = Files.list(Path.of("src/main/java/com/aiworkmate/agent/registry"))) {
            for (var source : sources.filter(p -> p.getFileName().toString().startsWith("Agent")
                    && (p.getFileName().toString().endsWith("Definitions.java")
                    || p.getFileName().toString().endsWith("ToolDefinition.java"))).sorted().toList()) {
                var type = Class.forName("com.aiworkmate.agent.registry." + source.getFileName().toString().replace(".java", ""));
                Object instance = type.getConstructor().newInstance();
                for (var method : type.getDeclaredMethods()) {
                    if (method.getReturnType() == ToolDefinition.class
                            && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{ObjectMapper.class})) {
                        method.setAccessible(true);
                        tools.add((ToolDefinition) method.invoke(instance, json));
                    }
                }
            }
        }
        return tools.stream().sorted(Comparator.comparing(ToolDefinition::code)).toList();
    }
}
