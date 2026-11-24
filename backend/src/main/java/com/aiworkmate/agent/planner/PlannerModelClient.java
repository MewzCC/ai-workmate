package com.aiworkmate.agent.planner;

public interface PlannerModelClient {
    String complete(String systemPrompt, String userPrompt);

    default int inputBudgetBytes() { return Integer.MAX_VALUE; }

    default String stream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onChunk) {
        String result = complete(systemPrompt, userPrompt);
        onChunk.accept(result);
        return result;
    }
}
