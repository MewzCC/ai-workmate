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
    LEAVE_CREATE_DRAFT("leave.createDraft"),
    LEAVE_SUBMIT("leave.submit"),
    LEAVE_APPLY("leave.apply"),
    APPROVAL_CONFIGURATION_QUERY("approval.configuration.query"),
    APPROVAL_TASK_QUERY("approval.task.query"),
    HR_ORGANIZATION_QUERY("hr.organization.query"),
    HR_EMPLOYEE_QUERY("hr.employee.query"),
    HR_CHANGE_QUERY("hr.change.query"),
    ATTENDANCE_QUERY("attendance.query"),
    ASSET_QUERY("asset.query"),
    MEETING_QUERY("meeting.query");

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
