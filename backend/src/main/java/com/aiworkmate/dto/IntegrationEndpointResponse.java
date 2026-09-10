package com.aiworkmate.dto;
import java.time.LocalDateTime;
import java.util.List;
public record IntegrationEndpointResponse(Long id,String code,String name,String upstreamCode,String method,
 String relativePath,String requestTemplate,String description,String status,Integer version,LocalDateTime updatedAt,
 boolean canManage,boolean canExecute,List<String> allowedTransitions) {}
