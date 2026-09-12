package com.aiworkmate.service.model;

import java.util.Set;

public final class NavigationComponentCatalog {

    private static final Set<String> ENABLED_COMPONENTS = Set.of(
            "DASHBOARD", "AI_WORKSPACE", "AI_TASK_CENTER", "ACCESS_CONTROL",
            "DICTIONARY", "TENANT_CONFIG", "DATA_PERMISSION", "AI_PERMISSION",
            "SUPPLIER", "CONTRACT", "EXPENSE", "BUDGET", "API_CENTER",
            "PAGE_ACTIONS", "RUNTIME_LOGS", "SANDBOX_REPLAY", "TODO_LIST",
            "LEAVE_FORM", "MY_APPLICATIONS", "AUDIT_CENTER", "APPROVAL_LIST",
            "APPROVAL_START", "APPROVAL_FORM", "FORM_ENGINE", "PROCESS_CONFIG",
            "APPROVAL_RULES", "ORG_TREE", "KNOWLEDGE_BASE", "MESSAGE_CENTER",
            "SYSTEM_CONFIG", "ATTENDANCE_CLOCK", "ATTENDANCE_EXCEPTION",
            "ATTENDANCE_REISSUE", "ATTENDANCE_STATISTICS", "ATTENDANCE_SETTINGS",
            "EMPLOYEE_FILES", "EMPLOYEE_CHANGE", "ASSET_LEDGER", "MEETING_ROOM",
            "VISITOR_BOOKING", "SEAL_USAGE"
    );

    private NavigationComponentCatalog() {
    }

    public static boolean supportsEnabledRoute(String routeKey, String componentKey) {
        if (componentKey == null || !ENABLED_COMPONENTS.contains(componentKey)) return false;
        return !"DASHBOARD".equals(componentKey) || "dashboard".equals(routeKey);
    }
}
