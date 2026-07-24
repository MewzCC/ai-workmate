package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveRouteRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,59}$", message = "路由编码须为小写字母、数字或连字符")
        String routeKey,
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,59}$", message = "父级路由编码不合法")
        String parentKey,
        @NotBlank
        @Size(max = 80)
        String name,
        @Pattern(regexp = "^/oa/[a-z][a-z0-9-]{1,59}$", message = "页面路径格式应为 /oa/page-key")
        String path,
        @Size(max = 60)
        String icon,
        @NotBlank
        @Pattern(regexp = "^(GROUP|MENU|PAGE)$", message = "路由类型不合法")
        String routeType,
        @Pattern(regexp = "^(DASHBOARD|AI_WORKSPACE|ACCESS_CONTROL)$", message = "页面组件不在安全白名单中")
        String componentKey,
        @NotNull
        Integer sortOrder,
        @NotNull
        Boolean enabled
) {
}
