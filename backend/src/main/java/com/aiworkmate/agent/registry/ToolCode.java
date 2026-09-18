package com.aiworkmate.agent.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Code-owned upper bound for Agent tools supported by this application build.
 * Database and tenant configuration may only narrow this set.
 */
public enum ToolCode {
    TODO_QUERY("todo.query"),
    LEAVE_MINE("leave.mine"),
    KNOWLEDGE_SEARCH("knowledge.search"),
    NOTIFICATION_MINE("notification.mine"),
    NOTIFICATION_MARK_READ("notification.markRead"),
    LEAVE_CREATE_DRAFT("leave.createDraft"),
    LEAVE_SUBMIT("leave.submit"),
    LEAVE_APPLY("leave.apply"),
    LEAVE_WITHDRAW("leave.withdraw"),
    APPROVAL_CONFIGURATION_QUERY("approval.configuration.query"),
    APPROVAL_TASK_QUERY("approval.task.query"),
    HR_ORGANIZATION_QUERY("hr.organization.query"),
    HR_EMPLOYEE_QUERY("hr.employee.query"),
    HR_CHANGE_QUERY("hr.change.query"),
    HR_CHANGE_APPLY("hr.change.apply"),
    ATTENDANCE_QUERY("attendance.query"),
    ATTENDANCE_REISSUE_APPLY("attendance.reissue.apply"),
    APPROVAL_APPLICATION_CREATE_DRAFT("approval.application.createDraft"),
    APPROVAL_APPLICATION_SUBMIT_DRAFT("approval.application.submitDraft"),
    APPROVAL_APPLICATION_WITHDRAW("approval.application.withdraw"),
    APPROVAL_APPLICATION_REOPEN("approval.application.reopen"),
    ASSET_QUERY("asset.query"),
    ASSET_CLAIM("asset.claim"),
    ASSET_RETURN("asset.return"),
    ASSET_REPAIR_START("asset.repair.start"),
    MEETING_QUERY("meeting.query"),
    MEETING_BOOK("meeting.book"),
    MEETING_CANCEL("meeting.cancel"),
    VISITOR_QUERY("visitor.query"),
    VISITOR_APPLY("visitor.apply"),
    VISITOR_CHECK_IN("visitor.checkIn"),
    VISITOR_MARK_ARRIVED("visitor.markArrived"),
    VISITOR_LEAVE("visitor.leave"),
    SEAL_QUERY("seal.query"),
    SEAL_APPLY("seal.apply"),
    SEAL_REGISTER_USE("seal.registerUse"),
    EXPENSE_QUERY("expense.query"),
    BUDGET_QUERY("budget.query"),
    CONTRACT_QUERY("contract.query"),
    SUPPLIER_QUERY("supplier.query"),
    INTEGRATION_ENDPOINT_QUERY("integration.endpoint.query"),
    PAGE_ACTION_QUERY("pageAction.query"),
    RUNTIME_LOG_QUERY("runtimeLog.query"),
    SANDBOX_REPLAY_QUERY("sandboxReplay.query"),
    ACCESS_GOVERNANCE_QUERY("accessGovernance.query"),
    DATA_PERMISSION_QUERY("dataPermission.query"),
    AI_PERMISSION_QUERY("aiPermission.query"),
    AUDIT_QUERY("audit.query"),
    TENANT_CONFIGURATION_QUERY("tenantConfiguration.query"),
    DICTIONARY_QUERY("dictionary.query"),
    SYSTEM_CAPABILITY_QUERY("systemCapability.query"),
    AGENT_TASK_MINE_QUERY("agentTask.mine.query");

    private static final Map<String, ToolCode> BY_CODE;
    private static final Set<String> CODES;

    static {
        Map<String, ToolCode> indexed = new LinkedHashMap<>();
        for (ToolCode value : values()) {
            if (indexed.putIfAbsent(value.code, value) != null) {
                throw new IllegalStateException("Duplicate Agent tool code: " + value.code);
            }
        }
        BY_CODE = Collections.unmodifiableMap(indexed);
        CODES = Set.copyOf(BY_CODE.keySet());
    }

    private final String code;

    ToolCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ToolCode fromCode(String code) {
        ToolCode value = BY_CODE.get(code);
        if (value == null) {
            throw new IllegalArgumentException("Tool code is outside the Phase 2 capability boundary");
        }
        return value;
    }

    public static boolean isSupported(String code) {
        return BY_CODE.containsKey(code);
    }

    public static Set<String> codes() {
        return CODES;
    }
}
