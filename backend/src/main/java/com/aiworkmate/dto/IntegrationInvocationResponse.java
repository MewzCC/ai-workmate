package com.aiworkmate.dto;
import java.time.LocalDateTime;
public record IntegrationInvocationResponse(Long id,String outcome,Integer httpStatus,Long durationMs,
 String responsePreview,String errorCode,String operatorLabel,LocalDateTime createdAt) {}
