package com.aiworkmate.agent.tool.port;

import java.util.List;

/**
 * Current-actor permission query boundary. Identity is supplied exclusively by the Tool Gateway.
 */
public interface UserPermissionToolPort {
    PermissionPage mine(ToolActorContext context, PermissionQuery query);

    record PermissionQuery(String keyword, int page, int size) { }

    record PermissionPage(
            String primaryRole,
            List<String> roles,
            List<String> dataScopes,
            long permissionVersion,
            List<String> permissions,
            int total,
            int page,
            int size
    ) {
        public PermissionPage {
            roles = List.copyOf(roles);
            dataScopes = List.copyOf(dataScopes);
            permissions = List.copyOf(permissions);
        }
    }
}
