package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
@RequiredArgsConstructor
public final class ApprovalApplicationReopenToolHandler implements ToolHandler {
    private final ApprovalApplicationToolPort approvalApplicationToolPort;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return ToolCode.APPROVAL_APPLICATION_REOPEN.code();
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        return objectMapper.valueToTree(approvalApplicationToolPort.reopen(
                context.actor(),
                requiredLong(arguments, "applicationId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1)));
    }
}
