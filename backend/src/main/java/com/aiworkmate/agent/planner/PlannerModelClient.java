package com.aiworkmate.agent.planner;

public interface PlannerModelClient {
    String complete(String systemPrompt, String userPrompt);

    default String stream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onChunk) {
        String result = complete(systemPrompt, userPrompt);
        onChunk.accept(result);
        return result;
    }
}
