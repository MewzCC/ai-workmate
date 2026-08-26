package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.service.LeaveWorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public final class LeaveCreateDraftToolHandler implements ToolHandler {
    private final LeaveWorkflowService leaveWorkflowService;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return "leave.createDraft";
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        LeaveApplicationRequest request = new LeaveApplicationRequest(
                requiredText(arguments, "leaveType"), optionalLong(arguments, "approverUserId"),
                date(arguments, "startDate"), requiredText(arguments, "startPeriod"),
                date(arguments, "endDate"), requiredText(arguments, "endPeriod"),
                requiredText(arguments, "reason"), null);
        String operationKey = "agent:" + context.taskId() + ":" + context.stepId() + ":leave.createDraft:v1";
        LeaveApplicationResponse created = leaveWorkflowService.createAgentDraft(
                context.userId(), request, operationKey);
        ObjectNode output = objectMapper.createObjectNode();
        output.put("applicationId", created.id());
        output.put("status", created.status());
        output.put("version", created.version());
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
