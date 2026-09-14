package com.aiworkmate.dto;
import java.util.List;
public record IntegrationDetailResponse(IntegrationEndpointResponse endpoint,List<IntegrationInvocationResponse> invocations) {}
