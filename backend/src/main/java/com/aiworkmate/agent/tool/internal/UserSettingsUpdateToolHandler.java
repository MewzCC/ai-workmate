package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.UserSettingsToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class UserSettingsUpdateToolHandler
        extends TypedWriteToolHandler<UserSettingsToolPort.UpdateCommand,
        UserSettingsToolPort.UpdateResult> {
    private final UserSettingsToolPort port;

    public UserSettingsUpdateToolHandler(UserSettingsToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.USER_SETTINGS_UPDATE, objectMapper);
        this.port = port;
    }

    @Override
    protected UserSettingsToolPort.UpdateCommand parseArguments(JsonNode arguments) {
        String model = requiredText(arguments, "model");
        if (!("deepseek-v4-flash".equals(model) || "deepseek-v4-pro".equals(model))
                || !arguments.path("stream").isBoolean()
                || !arguments.path("forcePdfOcr").isBoolean()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new UserSettingsToolPort.UpdateCommand(model,
                requiredInt(arguments, "maxContextRounds", 1, 20),
                arguments.path("stream").booleanValue(),
                arguments.path("forcePdfOcr").booleanValue());
    }

    @Override
    protected UserSettingsToolPort.UpdateResult invoke(
            TrustedToolContext context, UserSettingsToolPort.UpdateCommand command) {
        return port.update(context.actor(), command);
    }
}
