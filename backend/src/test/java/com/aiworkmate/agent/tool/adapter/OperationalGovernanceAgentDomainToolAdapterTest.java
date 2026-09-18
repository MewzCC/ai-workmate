package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.OperationalGovernanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AuditRecordResponse;
import com.aiworkmate.dto.SystemCapabilitiesResponse;
import com.aiworkmate.dto.SystemCapabilityStatusResponse;
import com.aiworkmate.service.AuditQueryService;
import com.aiworkmate.service.DataDictionaryService;
import com.aiworkmate.service.SystemCapabilityQueryService;
import com.aiworkmate.service.TenantConfigurationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationalGovernanceAgentDomainToolAdapterTest {
    private final AuditQueryService auditService = mock(AuditQueryService.class);
    private final TenantConfigurationService tenantService = mock(TenantConfigurationService.class);
    private final DataDictionaryService dictionaryService = mock(DataDictionaryService.class);
    private final SystemCapabilityQueryService capabilityService = mock(SystemCapabilityQueryService.class);
    private final OperationalGovernanceAgentDomainToolAdapter adapter =
            new OperationalGovernanceAgentDomainToolAdapter(
                    auditService, tenantService, dictionaryService, capabilityService);
    private final ToolActorContext actor = new ToolActorContext(1L, 2L, 3L, 4L, 0, "gateway-trace");

    @Test
    void auditMappingDropsActorResourceTraceAndSummary() {
        var query = new OperationalGovernanceToolPort.AuditQuery(null, null, null, null, null, 1, 20);
        when(auditService.query(2L, null, null, null, null, null, null, 1, 20))
                .thenReturn(PageResponse.of(List.of(new AuditRecordResponse(7L, 99L, "Secret Actor",
                        "CONTRACT", "secret-resource", "VIEW", "SUCCESS", "secret summary",
                        "secret-trace", LocalDateTime.of(2026, 9, 15, 10, 0))), 1, 1, 20));

        var result = adapter.auditRecords(actor, query);

        assertThat(result.records()).hasSize(1);
        assertThat(result.toString()).doesNotContain(
                "99", "Secret Actor", "secret-resource", "secret summary", "secret-trace");
    }

    @Test
    void capabilityMappingDropsProviderSummaryValues() {
        var status = new SystemCapabilityStatusResponse(true, true, "AVAILABLE",
                Map.of("endpoint", "https://secret.internal", "credential", "secret"));
        when(capabilityService.inspect(2L)).thenReturn(new SystemCapabilitiesResponse(
                Instant.EPOCH, status, status, status, status, status));

        var result = adapter.systemCapabilities(actor);

        assertThat(result.capabilities()).hasSize(5);
        assertThat(result.toString()).doesNotContain("secret", "endpoint", "credential", "internal");
    }
}
