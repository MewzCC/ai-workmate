package com.aiworkmate.service.impl;

import com.aiworkmate.common.*;
import com.aiworkmate.config.*;
import com.aiworkmate.dto.*;
import com.aiworkmate.mapper.AiConfigurationMapper;
import com.aiworkmate.service.*;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ai.chat.prompt.Prompt;
import java.net.URI;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiConfigurationServiceImpl implements AiConfigurationService {
    private final AiConfigurationMapper mapper;
    private final AiSecretCipher cipher;
    private final AiProviderRuntime runtime;
    private final UserAccessService access;
    private final BusinessAuditService audit;
    private ResolvedUserAccess authorize(Long id) {
        var actor=access.resolveActiveUser(id);
        if(actor==null || !actor.roles().contains("SUPER_ADMIN") || !actor.permissions().contains("access:manage"))
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }
    public AiConfigurationResponse get(Long id) { authorize(id); return response(); }
    public List<AiChannelResponse> channels(Long id) {
        authorize(id);
        return mapper.channels().stream().map(this::channelResponse).toList();
    }
    private AiChannelResponse channelResponse(Map<String,Object> row) {
        return new AiChannelResponse(((Number)row.get("id")).longValue(),(String)row.get("channel_name"),
                new AiConfigurationResponse(AiModelSettings.versionedUrl((String)row.get("base_url")),(String)row.get("model"),true,
                ((Number)row.get("version")).longValue(),"DATABASE",AiModelSettings.read(row,(String)row.get("model"))));
    }
    @Transactional
    public AiChannelResponse saveChannel(Long userId,long channelId,AiChannelRequest channel) {
        var actor=authorize(userId);
        if(channelId<1 || channelId>999999) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        var request=channel.configuration();
        var models=request.models()==null ? List.of(AiConfiguredModel.defaults(request.model())) : request.models();
        if(models.isEmpty() || models.size()>200 || models.stream().map(AiConfiguredModel::id).distinct().count()!=models.size()
                || models.stream().noneMatch(m -> m.id().equals(request.model()))
                || models.stream().anyMatch(m -> m.maxOutputTokens()>=m.contextWindow() || m.id().contains("::")
                    || (channelId!=1 && m.id().length()+Long.toString(channelId).length()+2>128)))
            throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.aiConfiguration.models");
        var old=mapper.channel(channelId);
        String encrypted;
        if(request.apiKey()==null || request.apiKey().isBlank()) {
            if(old==null) throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.aiConfiguration.key");
            encrypted=(String)old.get("encrypted_api_key");
        } else encrypted=cipher.encrypt(credential(request.apiKey()));
        String url=normalizeUrl(request.baseUrl());
        int changed=request.version()==0 ? mapper.insertChannel(channelId,channel.name().strip(),url,request.model(),encrypted,userId,AiModelSettings.write(models))
                : mapper.updateChannel(channelId,channel.name().strip(),url,request.model(),encrypted,userId,request.version(),AiModelSettings.write(models));
        if(changed!=1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        audit.recordTransactional(actor.tenantId(),userId,"AI_CONFIGURATION",Long.toString(channelId),"UPDATE","SUCCESS","AI channel configuration updated");
        return channelResponse(mapper.channel(channelId));
    }
    private AiConfigurationResponse response() {
        var row=mapper.load();
        if(row==null) return new AiConfigurationResponse(runtime.baseUrl(),runtime.catalog().get(0).id(),false,0,"ENVIRONMENT",runtime.catalog());
        return new AiConfigurationResponse(AiModelSettings.versionedUrl((String)row.get("base_url")),(String)row.get("model"),true,
                ((Number)row.get("version")).longValue(),"DATABASE",AiModelSettings.read(row,(String)row.get("model")));
    }
    public static String normalizeUrl(String url) {
        try {
            var uri=URI.create(url.strip());
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException();
            String normalized=uri.toString().replaceAll("/+$","");
            if(normalized.endsWith("/chat/completions") || normalized.endsWith("/models")) throw new IllegalArgumentException();
            return AiModelSettings.versionedUrl(normalized);
        } catch(RuntimeException e) { throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.aiConfiguration.url"); }
    }
    private String credential(String input) {
        return credential(input,null);
    }
    private String credential(String input,Long channelId) {
        if(input!=null && !input.isBlank()) {
            if(input.chars().anyMatch(Character::isWhitespace)) throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.aiConfiguration.key");
            return input;
        }
        var old=channelId==null ? mapper.load() : mapper.channel(channelId);
        if(old==null) throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.aiConfiguration.key");
        return cipher.decrypt((String)old.get("encrypted_api_key"));
    }
    @Transactional
    public AiConfigurationResponse save(Long id,AiConfigurationRequest request) {
        var actor=authorize(id);
        String url=normalizeUrl(request.baseUrl());
        var models=request.models()==null ? List.of(AiConfiguredModel.defaults(request.model())) : request.models();
        if(models.isEmpty() || models.size()>200 || models.stream().map(AiConfiguredModel::id).distinct().count()!=models.size()
                || models.stream().noneMatch(m -> m.id().equals(request.model()))
                || models.stream().anyMatch(m -> m.maxOutputTokens()>=m.contextWindow()))
            throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.aiConfiguration.models");
        String encrypted;
        if(request.apiKey()==null || request.apiKey().isBlank()) {
            var old=mapper.load();
            if(old==null) throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.aiConfiguration.key");
            encrypted=(String)old.get("encrypted_api_key");
        } else encrypted=cipher.encrypt(credential(request.apiKey()));
        String serialized=AiModelSettings.write(models);
        int changed=request.version()==0 ? mapper.insert(url,request.model(),encrypted,id,serialized)
                : mapper.update(url,request.model(),encrypted,id,request.version(),serialized);
        if(changed!=1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        audit.recordTransactional(actor.tenantId(),id,"AI_CONFIGURATION","1","UPDATE","SUCCESS","AI provider configuration updated");
        return response();
    }
    public List<AiConfiguredModel> discover(Long id,AiProviderProbeRequest request) {
        authorize(id);
        String url=normalizeUrl(request.baseUrl());
        String key=credential(request.apiKey(),request.channelId());
        try {
            var connection=(java.net.HttpURLConnection)URI.create(url+"/models").toURL().openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization","Bearer "+key);
            connection.setRequestProperty("Accept","application/json");
            try {
              if(connection.getResponseCode()!=200) throw new IllegalStateException();
              try(var stream=connection.getInputStream()) {
                byte[] bytes=stream.readNBytes(1048577);
                if(bytes.length>1048576) throw new IllegalStateException();
                var data=new ObjectMapper().readTree(bytes).path("data");
                if(!data.isArray() || data.size()>5000) throw new IllegalStateException();
                Map<String,AiConfiguredModel> models=new TreeMap<>();
                for(var item:data) {
                    String name=item.path("id").asText("");
                    if(!name.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) continue;
                    int context=Math.max(1024,Math.min(2097152,item.path("context_window").asInt(item.path("context_length").asInt(32768))));
                    int output=Math.max(16,Math.min(Math.min(393216,context-1),item.path("max_output_tokens").asInt(4096)));
                    models.put(name,new AiConfiguredModel(name,context,output,item.path("supports_images").asBoolean(false)));
                }
                if(models.isEmpty()) throw new IllegalStateException();
                return List.copyOf(models.values());
              }
            } finally { connection.disconnect(); }
        } catch(Exception e) { throw new BusinessException(ErrorCode.AI_CHAT_UNAVAILABLE,"error.aiConfiguration.modelsFailed"); }
    }
    public void test(Long id) { test(id,null); }
    public void test(Long id,AiProviderProbeRequest request) {
        authorize(id);
        try {
            var model=request==null ? runtime.model() : AiProviderRuntime.createModel(normalizeUrl(request.baseUrl()),
                    credential(request.apiKey(),request.channelId()),new AiConfiguredModel(request.model(),32768,64,false));
            if(request!=null && (request.model()==null || request.model().isBlank())) throw new IllegalArgumentException();
            String content=model.stream(new Prompt("Say hello briefly.")).map(response -> response.getResult()==null
                    || response.getResult().getOutput().getText()==null ? "" : response.getResult().getOutput().getText())
                    .filter(text -> !text.isBlank()).next().block(Duration.ofSeconds(60));
            if(content==null || content.isBlank()) throw new IllegalStateException();
        } catch(Exception e) { throw new BusinessException(ErrorCode.AI_CHAT_UNAVAILABLE,"error.aiConfiguration.testFailed"); }
    }
}
