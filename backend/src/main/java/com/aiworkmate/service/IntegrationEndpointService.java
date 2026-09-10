package com.aiworkmate.service;
import com.aiworkmate.dto.*;
public interface IntegrationEndpointService {
 IntegrationPageResponse list(Long userId,String keyword,String status,int page,int size);
 IntegrationDetailResponse detail(Long userId,Long id);
 IntegrationOptionsResponse options(Long userId);
 IntegrationEndpointResponse create(Long userId,IntegrationEndpointRequest request);
 IntegrationEndpointResponse update(Long userId,Long id,IntegrationEndpointRequest request);
 IntegrationEndpointResponse updateStatus(Long userId,Long id,IntegrationStatusRequest request);
 IntegrationInvocationResponse execute(Long userId,Long id,Integer version);
}
