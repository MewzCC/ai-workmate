package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.IntegrationEndpointRequest;
import com.aiworkmate.entity.IntegrationEndpoint;
import com.aiworkmate.entity.IntegrationInvocation;
import com.aiworkmate.mapper.IntegrationEndpointMapper;
import com.aiworkmate.mapper.IntegrationInvocationMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.IntegrationSandboxClient;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.service.model.SandboxCallResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationEndpointServiceImplTest {
 @Mock IntegrationEndpointMapper endpointMapper;@Mock IntegrationInvocationMapper invocationMapper;@Mock IntegrationSandboxClient client;@Mock UserAccessService accessService;@Mock BusinessAuditService auditService;
 IntegrationEndpointServiceImpl service;
 @BeforeEach void setup(){service=new IntegrationEndpointServiceImpl(endpointMapper,invocationMapper,client,accessService,auditService,new ObjectMapper());}
 @Test void rejectsCreateWithoutManagePermission(){when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:api-center")));assertThatThrownBy(()->service.create(7L,request("{}",null))).isInstanceOf(BusinessException.class);verifyNoInteractions(endpointMapper);}
 @Test void rejectsAbsoluteOrProtocolRelativeTargets(){stubManage();when(client.isRegistered("primary-sandbox")).thenReturn(true);assertThatThrownBy(()->service.create(7L,new IntegrationEndpointRequest("health","Health","primary-sandbox","GET","//evil.invalid/x",null,null,null))).isInstanceOf(BusinessException.class);verify(endpointMapper,never()).insert(any(IntegrationEndpoint.class));}
 @Test void rejectsSensitiveTemplateAtAnyDepth(){stubManage();when(client.isRegistered("primary-sandbox")).thenReturn(true);assertThatThrownBy(()->service.create(7L,request("{\"nested\":{\"api_key\":\"secret\"}}",null))).isInstanceOf(BusinessException.class);verify(endpointMapper,never()).insert(any(IntegrationEndpoint.class));}
 @Test void rejectsBodyForGet(){stubManage();when(client.isRegistered("primary-sandbox")).thenReturn(true);assertThatThrownBy(()->service.create(7L,request("{\"q\":1}",null))).isInstanceOf(BusinessException.class);}
 @Test void executionPersistsOnlyHashAndRedactedBoundedResult(){when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:api-center","integration:endpoint:execute")));IntegrationEndpoint endpoint=endpoint("ACTIVE",3);when(endpointMapper.selectOne(any())).thenReturn(endpoint);when(invocationMapper.selectOne(any())).thenReturn(null);when(client.execute("primary-sandbox","GET","/v1/health",null)).thenReturn(new SandboxCallResult("SUCCESS",200,18,"{\"ok\":true,\"nested\":{\"token\":\"must-not-persist\"}}",null));doAnswer(inv->{IntegrationInvocation row=inv.getArgument(0);row.setId(99L);return 1;}).when(invocationMapper).insert(any(IntegrationInvocation.class));
  var result=service.execute(7L,12L,3);ArgumentCaptor<IntegrationInvocation> saved=ArgumentCaptor.forClass(IntegrationInvocation.class);verify(invocationMapper).insert(saved.capture());assertThat(result.outcome()).isEqualTo("SUCCESS");assertThat(saved.getValue().getRequestHash()).hasSize(64);assertThat(saved.getValue().getResponsePreview()).contains("ok").contains("[REDACTED]").doesNotContain("must-not-persist");}
 @Test void blocksRapidRepeatedExecution(){when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:api-center","integration:endpoint:execute")));when(endpointMapper.selectOne(any())).thenReturn(endpoint("ACTIVE",3));IntegrationInvocation latest=new IntegrationInvocation();latest.setCreatedAt(LocalDateTime.now());when(invocationMapper.selectOne(any())).thenReturn(latest);assertThatThrownBy(()->service.execute(7L,12L,3)).isInstanceOf(BusinessException.class);verifyNoInteractions(client);}
 private void stubManage(){when(accessService.resolveActiveUser(7L)).thenReturn(access(List.of("route:api-center","integration:endpoint:manage")));}
 private IntegrationEndpointRequest request(String body,Integer version){return new IntegrationEndpointRequest("health","Health","primary-sandbox","GET","/v1/health",body,"check",version);}
 private IntegrationEndpoint endpoint(String status,int version){IntegrationEndpoint e=new IntegrationEndpoint();e.setId(12L);e.setTenantId(9L);e.setEndpointCode("HEALTH");e.setName("Health");e.setUpstreamCode("primary-sandbox");e.setHttpMethod("GET");e.setRelativePath("/v1/health");e.setStatus(status);e.setVersion(version);e.setDeleted(false);e.setUpdatedAt(LocalDateTime.now());return e;}
 private ResolvedUserAccess access(List<String> permissions){return new ResolvedUserAccess(7L,"system",9L,"SYSTEM_ADMIN",List.of("SYSTEM_ADMIN"),permissions,List.of("TENANT"),1L);}
}
