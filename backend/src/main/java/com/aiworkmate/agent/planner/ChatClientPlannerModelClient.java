package com.aiworkmate.agent.planner;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatClientPlannerModelClient implements PlannerModelClient {
    private final ChatClient chatClient;

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();
    }
}
