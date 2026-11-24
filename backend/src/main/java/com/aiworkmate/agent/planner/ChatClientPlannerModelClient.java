package com.aiworkmate.agent.planner;

import com.aiworkmate.config.AiProviderRuntime;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatClientPlannerModelClient implements PlannerModelClient {
    private final ChatClient chatClient;
    private final AiProviderRuntime runtime;

    @org.springframework.beans.factory.annotation.Autowired
    public ChatClientPlannerModelClient(ChatClient chatClient, AiProviderRuntime runtime) {
        this.chatClient = chatClient;
        this.runtime = runtime;
    }

    public ChatClientPlannerModelClient(ChatClient chatClient) { this(chatClient, null); }

    @Override
    public int inputBudgetBytes() { return runtime == null ? Integer.MAX_VALUE : runtime.plannerInputBudgetBytes(); }

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
