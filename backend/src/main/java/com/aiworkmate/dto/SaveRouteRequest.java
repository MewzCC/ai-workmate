package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveRouteRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,59}$", message = "{validation.routeKey.pattern}")
        String routeKey,
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,59}$", message = "{validation.routeKey.parentPattern}")
        String parentKey,
        @NotBlank
        @Size(max = 80)
        String name,
        @Pattern(regexp = "^/oa/[a-z][a-z0-9-]{1,59}$", message = "{validation.routeKey.pathPattern}")
        String path,
        @Size(max = 60)
        String icon,
        @NotBlank
        @Pattern(regexp = "^(GROUP|MENU|PAGE)$", message = "{validation.routeType.invalid}")
        String routeType,
        @Pattern(regexp = "^(DASHBOARD|AI_WORKSPACE|AI_TASK_CENTER|ACCESS_CONTROL|TODO_LIST|LEAVE_FORM|MY_APPLICATIONS|AUDIT_CENTER|APPROVAL_LIST|APPROVAL_START|APPROVAL_FORM|FORM_ENGINE|PROCESS_CONFIG|APPROVAL_RULES|ORG_TREE|KNOWLEDGE_BASE|MESSAGE_CENTER|SYSTEM_CONFIG|ATTENDANCE_CLOCK|ATTENDANCE_EXCEPTION|ATTENDANCE_REISSUE|ATTENDANCE_STATISTICS|ATTENDANCE_SETTINGS|EMPLOYEE_FILES|ASSET_LEDGER|MEETING_ROOM|VISITOR_BOOKING|SEAL_USAGE)$", message = "{validation.componentKey.invalid}")
        String componentKey,
        @NotNull
        Integer sortOrder,
        @NotNull
        Boolean enabled
) {
}
