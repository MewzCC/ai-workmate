package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;

@Component
@RequiredArgsConstructor
public final class LeaveWithdrawToolHandler implements ToolHandler {
    private final LeaveToolPort port;
    private final ObjectMapper mapper;
    @Override public String toolCode() { return ToolCode.LEAVE_WITHDRAW.code(); }
    @Override public String handlerVersion() { return "1.0.0"; }
    @Override public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        return mapper.valueToTree(port.withdraw(context.actor(),
                requiredLong(arguments, "applicationId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1)));
    }
}
