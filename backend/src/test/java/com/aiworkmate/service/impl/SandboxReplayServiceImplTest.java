package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.SandboxReplayRequest;
import com.aiworkmate.entity.IntegrationEndpoint;
import com.aiworkmate.entity.IntegrationInvocation;
import com.aiworkmate.entity.IntegrationReplayJob;
import com.aiworkmate.mapper.IntegrationEndpointMapper;
import com.aiworkmate.mapper.IntegrationInvocationMapper;
import com.aiworkmate.mapper.IntegrationReplayJobMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SandboxReplayServiceImplTest {
    @Mock IntegrationReplayJobMapper replayMapper;
    @Mock IntegrationInvocationMapper invocationMapper;
    @Mock IntegrationEndpointMapper endpointMapper;
    @Mock IntegrationSandboxClient sandboxClient;
    @Mock UserAccessService accessService;
    @Mock BusinessAuditService auditService;

    private IntegrationPayloadSecurity payloadSecurity;
    private SandboxReplayServiceImpl service;

    @BeforeEach
    void setUp() {
        payloadSecurity = new IntegrationPayloadSecurity(new ObjectMapper());
        service = new SandboxReplayServiceImpl(replayMapper, invocationMapper, endpointMapper,
                sandboxClient, accessService, auditService, payloadSecurity);
    }

    @Test
    void replaysOnlyMatchingCurrentConfigurationAndPersistsRedactedComparison() {
        stubActor(allPermissions());
        IntegrationEndpoint endpoint = endpoint();
        IntegrationInvocation baseline = baseline(endpoint);
        when(invocationMapper.selectOne(any())).thenReturn(baseline);
        when(endpointMapper.selectOne(any())).thenReturn(endpoint);
        when(sandboxClient.isAvailable("primary-sandbox")).thenReturn(true);
        when(replayMapper.selectOne(any())).thenReturn(null, null, completedJob());
        doAnswer(invocation -> {
            IntegrationReplayJob row = invocation.getArgument(0);
            row.setId(77L);
            return 1;
        }).when(replayMapper).insertReservation(any(IntegrationReplayJob.class));
        when(sandboxClient.execute("primary-sandbox", "POST", "/v1/check", "{\"sample\":1}"))
                .thenReturn(new SandboxCallResult("SUCCESS", 200, 31,
                        "{\"ok\":true,\"token\":\"must-not-persist\"}", null));
        when(replayMapper.update(any(), any())).thenReturn(1);

        var result = service.execute(7L,
                new SandboxReplayRequest(12L, "复核发布前兼容性", "12345678-1234-1234-1234-123456789abc"));

        ArgumentCaptor<IntegrationReplayJob> saved = ArgumentCaptor.forClass(IntegrationReplayJob.class);
        verify(replayMapper).insertReservation(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(9L);
        assertThat(saved.getValue().getBaselineResponsePreview()).contains("[REDACTED]")
                .doesNotContain("baseline-secret");
        assertThat(result.id()).isEqualTo(77L);
        verify(sandboxClient).execute("primary-sandbox", "POST", "/v1/check", "{\"sample\":1}");
    }

    @Test
    void returnsExistingReplayForSameActorIdempotencyKeyWithoutCallingSandbox() {
        stubActor(allPermissions());
        IntegrationReplayJob existing = completedJob();
        when(replayMapper.selectOne(any())).thenReturn(existing);

        var result = service.execute(7L,
                new SandboxReplayRequest(12L, "复核发布前兼容性", "12345678-1234-1234-1234-123456789abc"));

        assertThat(result.id()).isEqualTo(77L);
        verifyNoInteractions(invocationMapper, endpointMapper, sandboxClient);
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentBusinessArguments() {
        stubActor(allPermissions());
        when(replayMapper.selectOne(any())).thenReturn(completedJob());

        assertThatThrownBy(() -> service.execute(7L,
                new SandboxReplayRequest(99L, "复核另一个基线", "12345678-1234-1234-1234-123456789abc")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT.getErrorCode()));

        verifyNoInteractions(invocationMapper, endpointMapper, sandboxClient);
    }

    @Test
    void rejectsChangedConfigurationBeforeCallingSandbox() {
        stubActor(allPermissions());
        IntegrationEndpoint endpoint = endpoint();
        IntegrationInvocation baseline = baseline(endpoint);
        baseline.setRequestHash("0".repeat(64));
        when(replayMapper.selectOne(any())).thenReturn(null);
        when(invocationMapper.selectOne(any())).thenReturn(baseline);
        when(endpointMapper.selectOne(any())).thenReturn(endpoint);
        when(sandboxClient.isAvailable("primary-sandbox")).thenReturn(true);

        assertThatThrownBy(() -> service.execute(7L,
                new SandboxReplayRequest(12L, "验证历史接口行为", "12345678-1234-1234-1234-123456789abc")))
                .isInstanceOf(BusinessException.class);

        verify(sandboxClient, never()).execute(any(), any(), any(), any());
        verify(replayMapper, never()).insertReservation(any());
    }

    @Test
    void rejectsBaselineOutsideCurrentTenant() {
        stubActor(allPermissions());
        when(replayMapper.selectOne(any())).thenReturn(null);
        when(invocationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.execute(7L,
                new SandboxReplayRequest(99L, "验证历史接口行为", "12345678-1234-1234-1234-123456789abc")))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(endpointMapper, sandboxClient);
    }

    @Test
    void requiresDedicatedExecutePermission() {
        stubActor(List.of("route:sandbox-replay", "integration:replay:read"));

        assertThatThrownBy(() -> service.execute(7L,
                new SandboxReplayRequest(12L, "验证历史接口行为", "12345678-1234-1234-1234-123456789abc")))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(replayMapper, invocationMapper, sandboxClient);
    }

    private void stubActor(List<String> permissions) {
        when(accessService.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(
                7L, "system", 9L, "SYSTEM_ADMIN", List.of("SYSTEM_ADMIN"),
                permissions, List.of("TENANT"), 1L));
    }

    private List<String> allPermissions() {
        return List.of("route:sandbox-replay", "integration:replay:read", "integration:replay:execute");
    }

    private IntegrationEndpoint endpoint() {
        IntegrationEndpoint endpoint = new IntegrationEndpoint();
        endpoint.setId(5L);
        endpoint.setTenantId(9L);
        endpoint.setEndpointCode("ORDER-CHECK");
        endpoint.setName("订单校验");
        endpoint.setUpstreamCode("primary-sandbox");
        endpoint.setHttpMethod("POST");
        endpoint.setRelativePath("/v1/check");
        endpoint.setRequestTemplate("{\"sample\":1}");
        endpoint.setStatus("ACTIVE");
        endpoint.setDeleted(false);
        return endpoint;
    }

    private IntegrationInvocation baseline(IntegrationEndpoint endpoint) {
        IntegrationInvocation baseline = new IntegrationInvocation();
        baseline.setId(12L);
        baseline.setTenantId(9L);
        baseline.setEndpointId(endpoint.getId());
        baseline.setRequestHash(payloadSecurity.requestHash(endpoint.getHttpMethod(),
                endpoint.getRelativePath(), endpoint.getRequestTemplate()));
        baseline.setOutcome("SUCCESS");
        baseline.setHttpStatus(200);
        baseline.setDurationMs(20L);
        baseline.setResponsePreview("{\"ok\":true,\"token\":\"baseline-secret\"}");
        baseline.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        return baseline;
    }

    private IntegrationReplayJob completedJob() {
        IntegrationReplayJob job = new IntegrationReplayJob();
        job.setId(77L);
        job.setTenantId(9L);
        job.setSourceInvocationId(12L);
        job.setEndpointCode("ORDER-CHECK");
        job.setEndpointName("订单校验");
        job.setHttpMethod("POST");
        job.setRelativePath("/v1/check");
        job.setBaselineRequestHash("a".repeat(64));
        job.setBaselineOutcome("SUCCESS");
        job.setBaselineHttpStatus(200);
        job.setStatus("SUCCESS");
        job.setReplayHttpStatus(200);
        job.setReplayDurationMs(31L);
        job.setComparisonResult("MATCHED");
        job.setTraceId("b".repeat(32));
        job.setReason("复核发布前兼容性");
        job.setRequestedByLabel("system");
        job.setStartedAt(LocalDateTime.now());
        job.setCompletedAt(LocalDateTime.now());
        return job;
    }
}
