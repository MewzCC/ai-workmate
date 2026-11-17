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
    public ChatClient chatClient(AiProviderRuntime runtime,com.aiworkmate.service.AiUsageService usage) {
        return ChatClient.create(new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                var prepared=runtime.prepare(prompt);
                long start=System.nanoTime();
                try {
                    var response=prepared.model().call(prepared.prompt());
                    usage.record(prepared.channelId(),prepared.prompt().getOptions().getModel(),"SUCCESS",(System.nanoTime()-start)/1000000,response);
                    return response;
                } catch(RuntimeException error) {
                    usage.record(prepared.channelId(),prepared.prompt().getOptions().getModel(),"ERROR",(System.nanoTime()-start)/1000000,null);
                    throw error;
                }
            }
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.defer(() -> {
                    var prepared=runtime.prepare(prompt);
                    long start=System.nanoTime();
                    var latest=new java.util.concurrent.atomic.AtomicReference<ChatResponse>();
                    return prepared.model().stream(prepared.prompt()).timeout(Duration.ofSeconds(60))
                        .doOnNext(response -> {
                            if(response.getMetadata().getUsage()!=null && response.getMetadata().getUsage().getTotalTokens()!=null
                                && response.getMetadata().getUsage().getTotalTokens()>0) latest.set(response);
                        })
                        .doFinally(signal -> usage.record(prepared.channelId(),prepared.prompt().getOptions().getModel(),
                            signal==reactor.core.publisher.SignalType.ON_COMPLETE ? "SUCCESS" : signal==reactor.core.publisher.SignalType.CANCEL ? "CANCELLED" : "ERROR",
                            (System.nanoTime()-start)/1000000,latest.get()));
                });
            }
        });
    }
}
