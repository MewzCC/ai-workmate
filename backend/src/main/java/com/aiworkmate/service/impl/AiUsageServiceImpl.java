package com.aiworkmate.service.impl;
import com.aiworkmate.common.*;
import com.aiworkmate.dto.AiUsageBucket;
import com.aiworkmate.mapper.AiUsageMapper;
import com.aiworkmate.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.model.ChatResponse;
import java.util.List;
@Service @RequiredArgsConstructor @Slf4j
public class AiUsageServiceImpl implements AiUsageService {
 private final AiUsageMapper mapper;
 private final UserAccessService access;
 public void record(long channel,String model,String status,long durationMs,ChatResponse response) {
  var usage=response==null ? null : response.getMetadata().getUsage();
  boolean reported=usage!=null && usage.getTotalTokens()!=null && usage.getTotalTokens()>0;
  try {
   mapper.insert(channel,model,status,durationMs,
     reported && usage.getPromptTokens()!=null ? usage.getPromptTokens().longValue() : null,
     reported && usage.getCompletionTokens()!=null ? usage.getCompletionTokens().longValue() : null,
     reported ? usage.getTotalTokens().longValue() : null);
  } catch(RuntimeException error) { log.warn("AI usage persistence failed; channel={}, status={}",channel,status); }
 }
 public List<AiUsageBucket> report(Long userId,int days) {
  var actor=access.resolveActiveUser(userId);
  if(actor==null || !actor.roles().contains("SUPER_ADMIN") || !actor.permissions().contains("access:manage")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
  if(days!=1 && days!=7 && days!=30) throw new BusinessException(ErrorCode.REQUEST_INVALID);
  return mapper.buckets(days);
 }
}
