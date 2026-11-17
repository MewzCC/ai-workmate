package com.aiworkmate.dto;
public record AiUsageBucket(String day,long channelId,String model,long calls,long successes,long cancelled,
        double durationMs,long reportedCalls,Long inputTokens,Long outputTokens,Long totalTokens,String channelName) {}
