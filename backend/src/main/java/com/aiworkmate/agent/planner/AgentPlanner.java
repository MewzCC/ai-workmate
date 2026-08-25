package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.registry.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface AgentPlanner {
    PlannerCandidate plan(String input, String pageId, JsonNode pageContext, List<ToolDefinition> allowedTools);
}
