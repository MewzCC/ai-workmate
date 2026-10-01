package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Tenant-scoped read boundary for approval forms, processes and rules. */
public interface ApprovalConfigurationToolPort {
    Page query(ToolActorContext context, Query query);
    FormDraftResult createFormDraft(ToolActorContext context, FormDraft command);
    FormDraftResult updateFormDraft(ToolActorContext context, FormDraftUpdate command);
    FormDraftResult publishFormDraft(ToolActorContext context, VersionedForm command);
    ProcessDraftResult createProcessDraft(ToolActorContext context, ProcessDraft command);
    ProcessDraftResult updateProcessDraft(ToolActorContext context, ProcessDraftUpdate command);
    ProcessDraftResult publishProcessDraft(ToolActorContext context, VersionedProcess command);
    RuleDraftResult createRuleDraft(ToolActorContext context, RuleDraft command);
    RuleDraftResult updateRuleDraft(ToolActorContext context, RuleDraftUpdate command);
    RuleDraftResult enableRuleDraft(ToolActorContext context, VersionedRule command);

    enum Resource { FORM, PROCESS, RULE }

    record Query(Resource resource, String keyword, String status, int page, int size) { }

    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }

    record Item(long id, Resource resource, String key, String name, String description,
                String status, int version, String formName, String ruleType,
                Integer priority, LocalDateTime updatedAt) { }

    record FormDraft(String formKey, String formName, String description, List<FormField> fields) {
        public FormDraft { fields = List.copyOf(fields); }
    }

    record FormDraftUpdate(long formId, int version, String formName, String description,
                           List<FormField> fields) {
        public FormDraftUpdate { fields = List.copyOf(fields); }
    }

    record FormField(String name, String label, String type, boolean required,
                     String placeholder, List<String> options, String width) {
        public FormField { options = List.copyOf(options); }
    }

    record FormDraftResult(long formId, String formKey, String status, int version,
                           LocalDateTime updatedAt) implements ToolWriteReceipt { }

    record VersionedForm(long formId, int version) { }

    record ProcessDraft(String processKey, String processName, String description, Long formId,
                        List<ProcessNode> nodes) {
        public ProcessDraft { nodes = List.copyOf(nodes); }
    }

    record ProcessDraftUpdate(long processId, int version, String processName, String description,
                              Long formId, List<ProcessNode> nodes) {
        public ProcessDraftUpdate { nodes = List.copyOf(nodes); }
    }

    record ProcessNode(String nodeType, String nodeName, String approveType, String targetKey,
                       String mode, Boolean timeoutEnabled, Integer timeoutHours, String timeoutAction) { }

    record ProcessDraftResult(long processId, String processKey, String status, int version,
                              LocalDateTime updatedAt) implements ToolWriteReceipt { }

    record VersionedProcess(long processId, int version) { }

    record RuleDraft(String ruleKey, String ruleName, String ruleType, int priority,
                     String description, String logic, List<RuleCondition> conditions,
                     RuleAction action) {
        public RuleDraft { conditions = List.copyOf(conditions); }
    }

    record RuleCondition(String field, String operator, String value) { }

    record RuleDraftUpdate(long ruleId, int version, String ruleName, String ruleType, int priority,
                           String description, String logic, List<RuleCondition> conditions,
                           RuleAction action) {
        public RuleDraftUpdate { conditions = List.copyOf(conditions); }
    }

    record RuleAction(String appendNode, boolean enabled, String mode) { }

    record RuleDraftResult(long ruleId, String ruleKey, String status, int version,
                           LocalDateTime updatedAt) implements ToolWriteReceipt { }

    record VersionedRule(long ruleId, int version) { }
}
