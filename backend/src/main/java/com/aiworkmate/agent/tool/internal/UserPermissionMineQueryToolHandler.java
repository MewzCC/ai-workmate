package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.UserPermissionToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageNumber;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageSize;

@Component
public final class UserPermissionMineQueryToolHandler extends TypedReadToolHandler<
        UserPermissionToolPort.PermissionQuery, UserPermissionToolPort.PermissionPage> {
    private static final int MAX_KEYWORD_LENGTH = 80;
    private final UserPermissionToolPort port;

    public UserPermissionMineQueryToolHandler(UserPermissionToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.USER_PERMISSION_MINE_QUERY, objectMapper);
        this.port = port;
    }

    @Override
    protected UserPermissionToolPort.PermissionQuery parseArguments(JsonNode arguments) {
        String keyword = optionalText(arguments, "keyword");
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new UserPermissionToolPort.PermissionQuery(
                keyword, pageNumber(arguments), pageSize(arguments));
    }

    @Override
    protected UserPermissionToolPort.PermissionPage invoke(
            TrustedToolContext context, UserPermissionToolPort.PermissionQuery query) {
        return port.mine(context.actor(), query);
    }
}
