package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public final class LeaveApplyToolHandler implements ToolHandler {
    private final LeaveToolPort leaveToolPort;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return "leave.apply";
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        LeaveToolPort.Draft request = new LeaveToolPort.Draft(
                requiredText(arguments, "leaveType"), optionalLong(arguments, "approverUserId"),
                date(arguments, "startDate"), requiredText(arguments, "startPeriod"),
                date(arguments, "endDate"), requiredText(arguments, "endPeriod"),
                requiredText(arguments, "reason"));
        String operationKey = "agent:" + context.taskId() + ":" + context.stepId() + ":leave.apply:v1";
        LeaveToolPort.WriteResult application = leaveToolPort.apply(
                context.userId(), request, operationKey);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("applicationId", application.applicationId());
        output.put("status", application.status());
        output.put("version", application.version());
        output.put("approvalTaskId", application.approvalTaskId());
        return output;
    }

    private String requiredText(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return value.asText();
    }

    private Long optionalLong(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.canConvertToLong() || value.asLong() < 1) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return value.asLong();
    }

    private LocalDate date(JsonNode arguments, String field) {
        try {
            return LocalDate.parse(requiredText(arguments, field));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
    }
}
