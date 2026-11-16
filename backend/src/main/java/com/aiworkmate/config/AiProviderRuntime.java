package com.aiworkmate.config;

import com.aiworkmate.common.*;
import com.aiworkmate.dto.AiConfiguredModel;
import com.aiworkmate.mapper.AiConfigurationMapper;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class AiProviderRuntime {
    private final AiConfigurationMapper mapper;
    private final AiSecretCipher cipher;
    private final AiRuntimeProperties fallback;
    private final String defaultModel;
    public AiProviderRuntime(AiConfigurationMapper mapper, AiSecretCipher cipher, AiRuntimeProperties fallback,
            @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}") String model) {
        this.mapper=mapper; this.cipher=cipher; this.fallback=fallback; this.defaultModel=model;
    }
    public String baseUrl() { var row=mapper.load(); return AiModelSettings.versionedUrl(row==null ? fallback.getBaseUrl() : (String)row.get("base_url")); }
    public boolean configured() { return mapper.load()!=null || fallback.environmentConfigured(); }
    public List<AiConfiguredModel> catalog() {
        var row=mapper.load();
        var result=new java.util.ArrayList<>(AiModelSettings.read(row, row==null ? defaultModel : (String)row.get("model")));
        for(var channel:mapper.channels()) {
            long id=((Number)channel.get("id")).longValue();
            if(id==1) continue;
            for(var model:AiModelSettings.read(channel,(String)channel.get("model")))
                result.add(new AiConfiguredModel(id+"::"+model.id(),model.contextWindow(),model.maxOutputTokens(),model.supportsImages()));
        }
        return List.copyOf(result);
    }
    private Map<String,Object> resolve(String requested) {
        if(requested==null || !requested.contains("::")) return mapper.load();
        try {
            long id=Long.parseLong(requested.substring(0,requested.indexOf("::")));
            var row=mapper.channel(id);
            if(id<1 || row==null || row.isEmpty()) throw new IllegalArgumentException();
            return row;
        } catch(RuntimeException e) { throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.chat.model.invalid"); }
    }
    private String upstreamId(String requested) {
        return requested!=null && requested.contains("::") ? requested.substring(requested.indexOf("::")+2) : requested;
    }
    private AiConfiguredModel policy(Map<String,Object> row,String requested) {
        String id=requested==null || requested.isBlank() || "default".equals(requested)
                ? (row==null ? defaultModel : (String)row.get("model")) : requested;
        if(row==null) return AiConfiguredModel.defaults(id);
        return AiModelSettings.read(row,(String)row.get("model")).stream().filter(m -> m.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_INVALID,"validation.chat.model.invalid"));
    }
    public boolean supportsImages(String requested) { return policy(resolve(requested),upstreamId(requested)).supportsImages(); }
    public OpenAiChatModel model() {
        var row=mapper.load(); return create(row,policy(row,null));
    }
    private OpenAiChatModel create(Map<String,Object> row,AiConfiguredModel model) {
        String base=row==null ? fallback.getBaseUrl() : (String)row.get("base_url");
        String key=row==null ? fallback.getApiKey() : cipher.decrypt((String)row.get("encrypted_api_key"));
        if(key==null || key.isBlank() || "development-only-unconfigured".equals(key)) throw new BusinessException(ErrorCode.AI_CHAT_UNAVAILABLE);
        return createModel(AiModelSettings.versionedUrl(base),key,model);
    }
    public static OpenAiChatModel createModel(String url,String key,AiConfiguredModel model) {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); factory.setReadTimeout(60000);
        // The stored Base URL includes /v1. Explicit paths prevent Spring AI appending a second /v1.
        var api=OpenAiApi.builder().baseUrl(url).completionsPath("/chat/completions").apiKey(key)
                .restClientBuilder(org.springframework.web.client.RestClient.builder().requestFactory(factory)).build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(OpenAiChatOptions.builder()
                .model(model.id()).temperature(0.7).maxTokens(model.maxOutputTokens()).build()).build();
    }
    public record Prepared(OpenAiChatModel model,Prompt prompt,long channelId) {}
    public Prepared prepare(Prompt prompt) {
        String requested=prompt.getOptions()==null ? null : prompt.getOptions().getModel();
        var row=resolve(requested);
        var selected=policy(row,upstreamId(requested));
        long budget=selected.contextWindow()-selected.maxOutputTokens();
        long estimate=32;
        for(var message:prompt.getInstructions()) {
            estimate+=16+(message.getText()==null ? 0 : message.getText().getBytes(StandardCharsets.UTF_8).length);
            if(message instanceof UserMessage user && !user.getMedia().isEmpty()) {
                if(!selected.supportsImages()) throw new BusinessException(ErrorCode.REQUEST_INVALID,"error.aiConfiguration.imagesDisabled");
                estimate+=4096L*user.getMedia().size();
            }
        }
        if(estimate>budget) throw new BusinessException(ErrorCode.REQUEST_INVALID,"error.aiConfiguration.contextExceeded");
        var options=OpenAiChatOptions.builder().model(selected.id()).maxTokens(selected.maxOutputTokens()).streamUsage(true).build();
        long channelId=row==null ? 0 : requested!=null && requested.contains("::") ? Long.parseLong(requested.substring(0,requested.indexOf("::"))) : 1;
        return new Prepared(create(row,selected),new Prompt(prompt.getInstructions(),options),channelId);
    }
}
