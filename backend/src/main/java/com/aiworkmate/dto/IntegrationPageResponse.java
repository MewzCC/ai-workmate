package com.aiworkmate.dto;
import java.util.List;
public record IntegrationPageResponse(List<IntegrationEndpointResponse> records,long total,int page,int size,
 Stats stats,boolean canManage,boolean canExecute){public record Stats(long total,long active,long disabled,long successful,long failed) {}}
