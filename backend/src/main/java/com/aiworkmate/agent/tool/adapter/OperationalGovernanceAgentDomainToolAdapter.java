package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.OperationalGovernanceToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.SystemCapabilityStatusResponse;
import com.aiworkmate.service.AuditQueryService;
import com.aiworkmate.service.SystemCapabilityQueryService;
import com.aiworkmate.service.TenantConfigurationService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public class OperationalGovernanceAgentDomainToolAdapter implements OperationalGovernanceToolPort {
    private final AuditQueryService auditService;
    private final TenantConfigurationService tenantService;
    private final SystemCapabilityQueryService capabilityService;

    @Override
    public AuditPage auditRecords(ToolActorContext context, AuditQuery query) {
        var page = auditService.query(context.userId(), null, query.action(), query.resourceType(), query.result(),
                query.from(), query.to(), query.page(), query.size());
        return new AuditPage(page.records().stream()
                .map(x -> new AuditRecord(x.id(), x.resourceType(), x.action(), x.result(), x.createdAt()))
                .toList(), page.total(), page.page(), page.size());
    }

    @Override
    public TenantConfiguration tenantConfiguration(ToolActorContext context) {
        var x = tenantService.get(context.userId());
        return new TenantConfiguration(x.tenantName(), x.tenantShortName(), x.locale(), x.timezone(),
                x.fiscalYearStartMonth(), new FeatureSwitches(x.approvalEnabled(), x.attendanceEnabled(),
                x.assetEnabled(), x.meetingEnabled(), x.visitorEnabled(), x.sealEnabled()),
                x.defaultApprovalDays(), x.expenseCurrency(), x.passwordMinLength(), x.sessionTimeoutMinutes(),
                x.version(), x.updatedAt());
    }

    @Override
    public SystemCapabilities systemCapabilities(ToolActorContext context) {
        var x = capabilityService.inspect(context.userId());
        return new SystemCapabilities(x.checkedAt(), List.of(
                capability("ai", x.ai()), capability("embedding", x.embedding()), capability("ocr", x.ocr()),
                capability("minio", x.minio()), capability("redis", x.redis())));
    }

    private Capability capability(String code, SystemCapabilityStatusResponse status) {
        return new Capability(code, status.enabled(), status.available(), status.status());
    }
}
