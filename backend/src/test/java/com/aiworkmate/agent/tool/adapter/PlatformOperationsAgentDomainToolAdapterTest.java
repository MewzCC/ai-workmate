package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.IntegrationEndpointRequest;
import com.aiworkmate.dto.IntegrationEndpointResponse;
import com.aiworkmate.service.IntegrationEndpointService;
import com.aiworkmate.service.PageActionPolicyService;
import com.aiworkmate.service.RuntimeLogService;
import com.aiworkmate.service.SandboxReplayService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformOperationsAgentDomainToolAdapterTest {
    @Test
    void mapsDraftCreateWithoutAcceptingActorOrTenantArguments() {
        var endpointService = mock(IntegrationEndpointService.class);
        var adapter = new PlatformOperationsAgentDomainToolAdapter(endpointService,
                mock(PageActionPolicyService.class), mock(RuntimeLogService.class),
                mock(SandboxReplayService.class));
        var actor = new ToolActorContext(1L, 2L, 3L, 4L, 0, "trace");
        var request = new IntegrationEndpointRequest("health", "Health", "primary-sandbox",
                "GET", "/v1/health", null, "check", null);
        when(endpointService.createAgent(2L, request)).thenReturn(new IntegrationEndpointResponse(
                12L, "HEALTH", "Health", "primary-sandbox", "GET", "/v1/health", null,
                "check", "DRAFT", 0, LocalDateTime.of(2026, 10, 8, 23, 50), true, false,
                List.of("ACTIVE", "DISABLED")));

        var result = adapter.createEndpointDraft(actor, new PlatformOperationsToolPort.CreateEndpointCommand(
                "health", "Health", "primary-sandbox", "GET", "/v1/health", null, "check"));

        assertThat(result.endpointId()).isEqualTo(12L);
        assertThat(result.code()).isEqualTo("HEALTH");
        assertThat(result.status()).isEqualTo("DRAFT");
        verify(endpointService).createAgent(2L, request);
    }

    @Test
    void mapsOptimisticDraftUpdateWithoutNetworkExecutionArguments() {
        var endpointService = mock(IntegrationEndpointService.class);
        var adapter = new PlatformOperationsAgentDomainToolAdapter(endpointService,
                mock(PageActionPolicyService.class), mock(RuntimeLogService.class),
                mock(SandboxReplayService.class));
        var actor = new ToolActorContext(1L, 2L, 3L, 4L, 0, "trace");
        var request = new IntegrationEndpointRequest("HEALTH", "Health v2", "primary-sandbox",
                "GET", "/v2/health", null, "check v2", 3);
        when(endpointService.updateAgentDraft(2L, 12L, request)).thenReturn(new IntegrationEndpointResponse(
                12L, "HEALTH", "Health v2", "primary-sandbox", "GET", "/v2/health", null,
                "check v2", "DRAFT", 4, LocalDateTime.of(2026, 10, 8, 23, 55), true, false,
                List.of("ACTIVE", "DISABLED")));

        var result = adapter.updateEndpointDraft(actor, new PlatformOperationsToolPort.UpdateEndpointCommand(
                12L, 3, "HEALTH", "Health v2", "primary-sandbox", "GET", "/v2/health", null,
                "check v2"));

        assertThat(result.endpointId()).isEqualTo(12L);
        assertThat(result.version()).isEqualTo(4);
        assertThat(result.status()).isEqualTo("DRAFT");
        verify(endpointService).updateAgentDraft(2L, 12L, request);
    }
}
