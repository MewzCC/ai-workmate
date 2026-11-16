package com.aiworkmate.service;
import com.aiworkmate.dto.AiUsageBucket;
import org.springframework.ai.chat.model.ChatResponse;
import java.util.List;
public interface AiUsageService {
 void record(long channel,String model,String status,long durationMs,ChatResponse response);
 List<AiUsageBucket> report(Long userId,int days);
}
