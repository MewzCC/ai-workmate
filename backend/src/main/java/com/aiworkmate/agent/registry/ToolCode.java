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
    DASHBOARD_PREFERENCES_UPDATE("dashboard.preferences.update", SideEffect.SINGLE_WRITE),
    LEAVE_MINE("leave.mine"),
    KNOWLEDGE_SEARCH("knowledge.search"),
    KNOWLEDGE_DOCUMENT_CREATE_TEXT("knowledge.document.createText", SideEffect.SINGLE_WRITE),
    NOTIFICATION_MINE("notification.mine"),
    USER_PERMISSION_MINE_QUERY("userPermission.mine.query"),
    NOTIFICATION_MARK_READ("notification.markRead", SideEffect.SINGLE_WRITE),
    LEAVE_CREATE_DRAFT("leave.createDraft", SideEffect.SINGLE_WRITE),
    LEAVE_SUBMIT("leave.submit", SideEffect.SINGLE_WRITE),
    LEAVE_APPLY("leave.apply", SideEffect.SINGLE_WRITE),
    LEAVE_UPDATE_DRAFT("leave.updateDraft", SideEffect.SINGLE_WRITE),
    LEAVE_WITHDRAW("leave.withdraw", SideEffect.SINGLE_WRITE),
    LEAVE_REMIND("leave.remind", SideEffect.SINGLE_WRITE),
    APPROVAL_CONFIGURATION_QUERY("approval.configuration.query"),
    APPROVAL_TASK_QUERY("approval.task.query"),
    APPROVAL_TASK_APPROVE("approval.task.approve", SideEffect.SINGLE_WRITE),
    APPROVAL_TASK_REJECT("approval.task.reject", SideEffect.SINGLE_WRITE),
    APPROVAL_TASK_TRANSFER("approval.task.transfer", SideEffect.SINGLE_WRITE),
    APPROVAL_TASK_COPY("approval.task.copy", SideEffect.SINGLE_WRITE),
    APPROVAL_TASK_ADD_SIGN("approval.task.addSign", SideEffect.SINGLE_WRITE),
    HR_ORGANIZATION_QUERY("hr.organization.query"),
    HR_EMPLOYEE_QUERY("hr.employee.query"),
    HR_CHANGE_QUERY("hr.change.query"),
    HR_CHANGE_APPLY("hr.change.apply", SideEffect.SINGLE_WRITE),
    HR_CHANGE_APPROVE("hr.change.approve", SideEffect.SINGLE_WRITE),
    HR_CHANGE_REJECT("hr.change.reject", SideEffect.SINGLE_WRITE),
    HR_CHANGE_WITHDRAW("hr.change.withdraw", SideEffect.SINGLE_WRITE),
    ATTENDANCE_QUERY("attendance.query"),
    ATTENDANCE_CLOCK("attendance.clock", SideEffect.SINGLE_WRITE),
    ATTENDANCE_REISSUE_APPLY("attendance.reissue.apply", SideEffect.SINGLE_WRITE),
    ATTENDANCE_REISSUE_DECIDE("attendance.reissue.decide", SideEffect.SINGLE_WRITE),
    ATTENDANCE_SETTINGS_UPDATE("attendance.settings.update", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_CREATE_DRAFT("approval.application.createDraft", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_UPDATE_DRAFT("approval.application.updateDraft", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_SUBMIT_DRAFT("approval.application.submitDraft", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_CANCEL_DRAFT("approval.application.cancelDraft", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_WITHDRAW("approval.application.withdraw", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_REOPEN("approval.application.reopen", SideEffect.SINGLE_WRITE),
    APPROVAL_APPLICATION_REMIND("approval.application.remind", SideEffect.SINGLE_WRITE),
    ASSET_QUERY("asset.query"),
    ASSET_CLAIM("asset.claim", SideEffect.SINGLE_WRITE),
    ASSET_RETURN("asset.return", SideEffect.SINGLE_WRITE),
    ASSET_REPAIR_START("asset.repair.start", SideEffect.SINGLE_WRITE),
    ASSET_TRANSFER("asset.transfer", SideEffect.SINGLE_WRITE),
    ASSET_REPAIR_COMPLETE("asset.repair.complete", SideEffect.SINGLE_WRITE),
    ASSET_INVENTORY("asset.inventory", SideEffect.SINGLE_WRITE),
    ASSET_SCRAP("asset.scrap", SideEffect.SINGLE_WRITE),
    MEETING_QUERY("meeting.query"),
    MEETING_BOOK("meeting.book", SideEffect.SINGLE_WRITE),
    MEETING_CANCEL("meeting.cancel", SideEffect.SINGLE_WRITE),
    VISITOR_QUERY("visitor.query"),
    VISITOR_APPLY("visitor.apply", SideEffect.SINGLE_WRITE),
    VISITOR_CHECK_IN("visitor.checkIn", SideEffect.SINGLE_WRITE),
    VISITOR_MARK_ARRIVED("visitor.markArrived", SideEffect.SINGLE_WRITE),
    VISITOR_LEAVE("visitor.leave", SideEffect.SINGLE_WRITE),
    VISITOR_WITHDRAW("visitor.withdraw", SideEffect.SINGLE_WRITE),
    VISITOR_NO_SHOW("visitor.noShow", SideEffect.SINGLE_WRITE),
    SEAL_QUERY("seal.query"),
    SEAL_APPLY("seal.apply", SideEffect.SINGLE_WRITE),
    SEAL_REGISTER_USE("seal.registerUse", SideEffect.SINGLE_WRITE),
    SEAL_WITHDRAW("seal.withdraw", SideEffect.SINGLE_WRITE),
    SEAL_RETURN("seal.return", SideEffect.SINGLE_WRITE),
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
    CONTRACT_UPDATE_DRAFT("contract.updateDraft", SideEffect.SINGLE_WRITE),
    CONTRACT_UPDATE_STATUS("contract.updateStatus", SideEffect.SINGLE_WRITE),
    CONTRACT_UPDATE_FULFILLMENT("contract.updateFulfillment", SideEffect.SINGLE_WRITE),
    CONTRACT_RECORD_PAYMENT("contract.recordPayment", SideEffect.SINGLE_WRITE),
    CONTRACT_REMIND("contract.remind", SideEffect.SINGLE_WRITE),
    SUPPLIER_QUERY("supplier.query"),
    SUPPLIER_CREATE_DRAFT("supplier.createDraft", SideEffect.SINGLE_WRITE),
    SUPPLIER_UPDATE_DRAFT("supplier.updateDraft", SideEffect.SINGLE_WRITE),
    SUPPLIER_UPDATE_STATUS("supplier.updateStatus", SideEffect.SINGLE_WRITE),
    INTEGRATION_ENDPOINT_QUERY("integration.endpoint.query"),
    PAGE_ACTION_QUERY("pageAction.query"),
    RUNTIME_LOG_QUERY("runtimeLog.query"),
    OBSERVABILITY_PREFERENCES_UPDATE("observability.preferences.update", SideEffect.SINGLE_WRITE),
    OBSERVABILITY_THRESHOLDS_UPDATE("observability.thresholds.update", SideEffect.SINGLE_WRITE),
    SANDBOX_REPLAY_QUERY("sandboxReplay.query"),
    ACCESS_GOVERNANCE_QUERY("accessGovernance.query"),
    DATA_PERMISSION_QUERY("dataPermission.query"),
    AI_PERMISSION_QUERY("aiPermission.query"),
    AUDIT_QUERY("audit.query"),
    TENANT_CONFIGURATION_QUERY("tenantConfiguration.query"),
    DICTIONARY_QUERY("dictionary.query"),
    SYSTEM_CAPABILITY_QUERY("systemCapability.query"),
    USER_SETTINGS_UPDATE("userSettings.update", SideEffect.SINGLE_WRITE),
    AGENT_TASK_MINE_QUERY("agentTask.mine.query"),
    AGENT_TASK_CANCEL("agentTask.cancel", SideEffect.SINGLE_WRITE);

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
