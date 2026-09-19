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
    NOTIFICATION_MARK_READ("notification.markRead", SideEffect.SINGLE_WRITE),
    LEAVE_CREATE_DRAFT("leave.createDraft", SideEffect.SINGLE_WRITE),
    LEAVE_SUBMIT("leave.submit", SideEffect.SINGLE_WRITE),
    LEAVE_APPLY("leave.apply", SideEffect.SINGLE_WRITE),
    LEAVE_WITHDRAW("leave.withdraw", SideEffect.SINGLE_WRITE),
    APPROVAL_CONFIGURATION_QUERY("approval.configuration.query"),
    APPROVAL_TASK_QUERY("approval.task.query"),
    HR_ORGANIZATION_QUERY("hr.organization.query"),
    HR_EMPLOYEE_QUERY("hr.employee.query"),
    HR_CHANGE_QUERY("hr.change.query"),
    HR_CHANGE_APPLY("hr.change.apply", SideEffect.SINGLE_WRITE),
    ATTENDANCE_QUERY("attendance.query"),
    ATTENDANCE_REISSUE_APPLY("attendance.reissue.apply", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_CREATE_DRAFT("approval.application.createDraft", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_SUBMIT_DRAFT("approval.application.submitDraft", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_WITHDRAW("approval.application.withdraw", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_REOPEN("approval.application.reopen", SideEffect.SINGLE_WRITE),
    ASSET_QUERY("asset.query"),
    ASSET_CLAIM("asset.claim", SideEffect.SINGLE_WRITE),
    ASSET_RETURN("asset.return", SideEffect.SINGLE_WRITE),
    ASSET_REPAIR_START("asset.repair.start", SideEffect.SINGLE_WRITE),
    MEETING_QUERY("meeting.query"),
    MEETING_BOOK("meeting.book", SideEffect.SINGLE_WRITE),
    MEETING_CANCEL("meeting.cancel", SideEffect.SINGLE_WRITE),
    VISITOR_QUERY("visitor.query"),
    VISITOR_APPLY("visitor.apply", SideEffect.SINGLE_WRITE),
    VISITOR_CHECK_IN("visitor.checkIn", SideEffect.SINGLE_WRITE),
    VISITOR_MARK_ARRIVED("visitor.markArrived", SideEffect.SINGLE_WRITE),
    VISITOR_LEAVE("visitor.leave", SideEffect.SINGLE_WRITE),
    SEAL_QUERY("seal.query"),
    SEAL_APPLY("seal.apply", SideEffect.SINGLE_WRITE),
    SEAL_REGISTER_USE("seal.registerUse", SideEffect.SINGLE_WRITE),
    EXPENSE_QUERY("expense.query"),
    EXPENSE_CREATE_DRAFT("expense.createDraft", SideEffect.SINGLE_WRITE),
    EXPENSE_UPDATE_DRAFT("expense.updateDraft", SideEffect.SINGLE_WRITE),
    EXPENSE_SUBMIT_DRAFT("expense.submitDraft", SideEffect.SINGLE_WRITE),
    EXPENSE_WITHDRAW("expense.withdraw", SideEffect.SINGLE_WRITE),
    EXPENSE_REOPEN("expense.reopen", SideEffect.SINGLE_WRITE),
    BUDGET_QUERY("budget.query"),
    BUDGET_CREATE_DRAFT("budget.createDraft", SideEffect.SINGLE_WRITE),
    BUDGET_UPDATE_DRAFT("budget.updateDraft", SideEffect.SINGLE_WRITE),
    BUDGET_ACTIVATE_DRAFT("budget.activateDraft", SideEffect.SINGLE_WRITE),
    BUDGET_CANCEL_DRAFT("budget.cancelDraft", SideEffect.SINGLE_WRITE),
    CONTRACT_QUERY("contract.query"),
    CONTRACT_CREATE_DRAFT("contract.createDraft", SideEffect.SINGLE_WRITE),
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
    private final SideEffect sideEffect;

    ToolCode(String code) {
        this(code, SideEffect.NONE);
    }

    ToolCode(String code, SideEffect sideEffect) {
        this.code = code;
        this.sideEffect = sideEffect;
    }

    public String code() {
        return code;
    }

    public SideEffect sideEffect() {
        return sideEffect;
    }

    public boolean isWrite() {
        return sideEffect == SideEffect.SINGLE_WRITE;
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
