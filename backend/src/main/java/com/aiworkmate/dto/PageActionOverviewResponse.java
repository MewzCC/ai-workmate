package com.aiworkmate.dto;

import java.util.List;

public record PageActionOverviewResponse(
        List<Page> pages,
        int total,
        int enabled,
        int disabled,
        boolean canManage
) {
    public record Page(String pageId, List<Action> actions) {}

    public record Action(String pageId, String toolCode, String name, String description,
                         String riskLevel, String sideEffect, String confirmationPolicy,
                         List<String> requiredPermissions, boolean enabled,
                         boolean explicitlyConfigured, int version) {}
}
