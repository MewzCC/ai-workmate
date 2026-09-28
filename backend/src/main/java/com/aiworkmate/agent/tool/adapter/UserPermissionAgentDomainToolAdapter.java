package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.UserPermissionToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public class UserPermissionAgentDomainToolAdapter implements UserPermissionToolPort {
    private static final int MAX_ROLE_ITEMS = 20;
    private final UserAccessService userAccessService;

    @Override
    public PermissionPage mine(ToolActorContext context, PermissionQuery query) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(context.userId());
        if (access == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        if (!Objects.equals(access.tenantId(), context.tenantId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        List<String> roles = normalized(access.roles(), MAX_ROLE_ITEMS);
        List<String> dataScopes = normalized(access.dataScopes(), MAX_ROLE_ITEMS);
        List<String> matchingPermissions = normalized(access.permissions(), Integer.MAX_VALUE);
        if (query.keyword() != null) {
            String keyword = query.keyword().toLowerCase(Locale.ROOT);
            matchingPermissions = matchingPermissions.stream()
                    .filter(permission -> permission.toLowerCase(Locale.ROOT).contains(keyword))
                    .toList();
        }

        int total = matchingPermissions.size();
        long offset = (long) (query.page() - 1) * query.size();
        List<String> pageItems = offset >= total
                ? List.of()
                : matchingPermissions.subList((int) offset, Math.min(total, (int) offset + query.size()));
        return new PermissionPage(
                access.role() == null ? "" : access.role(),
                roles,
                dataScopes,
                access.permissionVersion() == null ? 0L : Math.max(0L, access.permissionVersion()),
                pageItems,
                total,
                query.page(),
                query.size()
        );
    }

    private List<String> normalized(List<String> values, int limit) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .limit(limit)
                .toList();
    }
}
