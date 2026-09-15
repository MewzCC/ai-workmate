package com.aiworkmate.agent.tool.port;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary for audit, tenant configuration, dictionary and capability services. */
public interface OperationalGovernanceToolPort {
    AuditPage auditRecords(ToolActorContext context, AuditQuery query);
    TenantConfiguration tenantConfiguration(ToolActorContext context);
    DictionaryOverview dictionaries(ToolActorContext context, DictionaryQuery query);
    SystemCapabilities systemCapabilities(ToolActorContext context);

    record AuditQuery(String action, String resourceType, String result, LocalDateTime from,
                      LocalDateTime to, int page, int size) { }
    record DictionaryQuery(String keyword, String status) { }
    record AuditPage(List<AuditRecord> records, long total, int page, int size) {
        public AuditPage { records = List.copyOf(records); }
    }
    record AuditRecord(long id, String resourceType, String action, String result, LocalDateTime createdAt) { }
    record TenantConfiguration(String tenantName, String tenantShortName, String locale, String timezone,
                               int fiscalYearStartMonth, FeatureSwitches features, int defaultApprovalDays,
                               String expenseCurrency, int passwordMinLength, int sessionTimeoutMinutes,
                               int version, LocalDateTime updatedAt) { }
    record FeatureSwitches(boolean approval, boolean attendance, boolean asset, boolean meeting,
                           boolean visitor, boolean seal) { }
    record DictionaryOverview(List<DictionaryType> records, boolean canManage) {
        public DictionaryOverview { records = List.copyOf(records); }
    }
    record DictionaryType(String code, String name, String description, String status, int sortOrder,
                          int itemCount, int activeItemCount, int version, LocalDateTime updatedAt) { }
    record SystemCapabilities(Instant checkedAt, List<Capability> capabilities) {
        public SystemCapabilities { capabilities = List.copyOf(capabilities); }
    }
    record Capability(String code, boolean enabled, boolean available, String status) { }
}
