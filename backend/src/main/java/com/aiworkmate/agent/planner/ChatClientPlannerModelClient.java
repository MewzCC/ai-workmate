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

    @Override
    public String stream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onChunk) {
        StringBuilder result = new StringBuilder();
        chatClient.prompt().system(systemPrompt).user(userPrompt).stream().content()
                .doOnNext(chunk -> { onChunk.accept(chunk); result.append(chunk); })
                .blockLast();
        return result.toString();
    }
}
