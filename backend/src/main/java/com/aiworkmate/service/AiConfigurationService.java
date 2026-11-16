package com.aiworkmate.service;
import com.aiworkmate.dto.*;
public interface AiConfigurationService {
 java.util.List<AiChannelResponse> channels(Long userId);
 AiChannelResponse saveChannel(Long userId,long channelId,AiChannelRequest request);
 AiConfigurationResponse get(Long userId);
 AiConfigurationResponse save(Long userId, AiConfigurationRequest request);
 void test(Long userId);
 void test(Long userId,AiProviderProbeRequest request);
 java.util.List<AiConfiguredModel> discover(Long userId,AiProviderProbeRequest request);
}
