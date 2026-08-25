package com.aiworkmate.agent.planner;

public interface PlannerModelClient {
    String complete(String systemPrompt, String userPrompt);
}
