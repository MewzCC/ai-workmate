package com.aiworkmate.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.*;
import reactor.core.publisher.Flux;
import java.time.Duration;

@Configuration
public class AiConfig {
    @Bean
    public ChatClient chatClient(AiProviderRuntime runtime) {
        return ChatClient.create(new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                var prepared=runtime.prepare(prompt);
                return prepared.model().call(prepared.prompt());
            }
            public Flux<ChatResponse> stream(Prompt prompt) {
                var prepared=runtime.prepare(prompt);
                return prepared.model().stream(prepared.prompt()).timeout(Duration.ofSeconds(60));
            }
        });
    }
}
