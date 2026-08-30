package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.LeaveWorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class LeaveSubmitToolHandler implements ToolHandler {
    private final LeaveWorkflowService leaveWorkflowService;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return "leave.submit";
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        long applicationId = requiredLong(arguments, "applicationId", 1);
        int version = Math.toIntExact(requiredLong(arguments, "version", 0));
        LeaveApplicationResponse submitted = leaveWorkflowService.submitAgent(
                context.userId(), applicationId, new VersionRequest(version), context.taskId());
        ObjectNode output = objectMapper.createObjectNode();
        output.put("applicationId", submitted.id());
        output.put("status", submitted.status());
        output.put("version", submitted.version());
        return output;
    }

    private long requiredLong(JsonNode arguments, String field, long minimum) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.asLong() < minimum) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return value.asLong();
    }
}
