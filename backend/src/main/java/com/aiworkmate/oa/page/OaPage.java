package com.aiworkmate.oa.page;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Framework-neutral upper bound for enabled OA PAGE routes.
 *
 * <p>Navigation, access control and Agent page capabilities share this contract. Database and
 * tenant configuration may disable an entry but cannot introduce or remap an enabled page.</p>
 */
public enum OaPage {
    DASHBOARD("dashboard", "DASHBOARD"),
    AI_WORKSPACE("ai-workspace", "AI_WORKSPACE"),
    AI_TASKS("ai-tasks", "AI_TASK_CENTER"),
    TODO("todo", "TODO_LIST"),
    MESSAGES("messages", "MESSAGE_CENTER"),
    LEAVE_APPLICATION("leave-application", "LEAVE_FORM"),
    MY_APPLICATIONS("my-applications", "MY_APPLICATIONS"),
    APPROVAL_LIST("approval-list", "APPROVAL_LIST"),
    APPROVAL_START("approval-start", "APPROVAL_START"),
    APPROVAL_FORM("approval-form", "APPROVAL_FORM"),
    FORM_ENGINE("form-engine", "FORM_ENGINE"),
    PROCESS_CONFIG("process-config", "PROCESS_CONFIG"),
    APPROVAL_RULES("approval-rules", "APPROVAL_RULES"),
    ORG_TREE("org-tree", "ORG_TREE"),
    EMPLOYEE_FILES("employee-files", "EMPLOYEE_FILES"),
    EMPLOYEE_CHANGE("employee-change", "EMPLOYEE_CHANGE"),
    ASSET_LEDGER("asset-ledger", "ASSET_LEDGER"),
    MEETING_ROOM("meeting-room", "MEETING_ROOM"),
    VISITOR_BOOKING("visitor-booking", "VISITOR_BOOKING"),
    SEAL_USAGE("seal-usage", "SEAL_USAGE"),
    EXPENSE("expense", "EXPENSE"),
    BUDGET("budget", "BUDGET"),
    CONTRACTS("contracts", "CONTRACT"),
    SUPPLIERS("suppliers", "SUPPLIER"),
    API_CENTER("api-center", "API_CENTER"),
    PAGE_ACTIONS("page-actions", "PAGE_ACTIONS"),
    RUNTIME_LOGS("runtime-logs", "RUNTIME_LOGS"),
    SANDBOX_REPLAY("sandbox-replay", "SANDBOX_REPLAY"),
    ATTENDANCE_CLOCK("attendance-clock", "ATTENDANCE_CLOCK"),
    ATTENDANCE_EXCEPTION("attendance-exception", "ATTENDANCE_EXCEPTION"),
    ATTENDANCE_REISSUE("attendance-reissue", "ATTENDANCE_REISSUE"),
    ATTENDANCE_STATISTICS("attendance-statistics", "ATTENDANCE_STATISTICS"),
    ATTENDANCE_SETTINGS("attendance-settings", "ATTENDANCE_SETTINGS"),
    ACCESS_CONTROL("access-control", "ACCESS_CONTROL"),
    DATA_PERMISSION("data-permission", "DATA_PERMISSION"),
    AI_PERMISSION("ai-permission", "AI_PERMISSION"),
    KNOWLEDGE_BASE("knowledge-base", "KNOWLEDGE_BASE"),
    AUDIT_CENTER("audit-center", "AUDIT_CENTER"),
    TENANT_CONFIG("tenant-config", "TENANT_CONFIG"),
    DICTIONARY("dictionary", "DICTIONARY"),
    SYSTEM_CONFIG("system-config", "SYSTEM_CONFIG");

    private static final Map<String, OaPage> BY_ROUTE_KEY;

    static {
        Map<String, OaPage> indexed = new LinkedHashMap<>();
        for (OaPage page : values()) {
            if (indexed.putIfAbsent(page.routeKey, page) != null) {
                throw new IllegalStateException("Duplicate OA page route key: " + page.routeKey);
            }
        }
        BY_ROUTE_KEY = Collections.unmodifiableMap(indexed);
    }

    private final String routeKey;
    private final String componentKey;

    OaPage(String routeKey, String componentKey) {
        this.routeKey = routeKey;
        this.componentKey = componentKey;
    }

    public String routeKey() {
        return routeKey;
    }

    public String componentKey() {
        return componentKey;
    }

    public static boolean supportsEnabledRoute(String routeKey, String componentKey) {
        OaPage page = BY_ROUTE_KEY.get(routeKey);
        return page != null && page.componentKey.equals(componentKey);
    }
}
