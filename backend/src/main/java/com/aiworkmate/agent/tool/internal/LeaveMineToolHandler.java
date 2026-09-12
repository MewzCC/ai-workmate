package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class LeaveMineToolHandler implements ToolHandler {
    private static final int MAX_SIZE = 50;

    private final LeaveToolPort leaveToolPort;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return "leave.mine";
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        Long applicationId = positiveLong(arguments, "applicationId");
        String status = text(arguments, "status");
        boolean hasListArguments = status != null || arguments.has("page") || arguments.has("size");
        if (applicationId != null && hasListArguments) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }

        LeaveToolPort.Page result;
        if (applicationId != null) {
            result = new LeaveToolPort.Page(
                    java.util.List.of(leaveToolPort.getMine(context.userId(), applicationId)),
                    1, 1, 1);
        } else {
            int page = positiveInt(arguments, "page", 1);
            int size = Math.min(MAX_SIZE, positiveInt(arguments, "size", 20));
            result = leaveToolPort.mine(context.userId(), new LeaveToolPort.Query(status, page, size));
        }
        return output(result);
    }

    private JsonNode output(LeaveToolPort.Page result) {
        ObjectNode output = objectMapper.createObjectNode();
        ArrayNode items = output.putArray("items");
        result.items().forEach(application -> append(items, application));
        output.put("total", result.total());
        output.put("page", result.page());
        output.put("size", result.size());
        return output;
    }

    private void append(ArrayNode items, LeaveToolPort.Item application) {
        ObjectNode item = items.addObject();
        item.put("id", application.id());
        if (application.approverName() != null) {
            item.put("approverName", application.approverName());
        }
        item.put("leaveType", application.leaveType());
        item.put("startDate", application.startDate().toString());
        item.put("startPeriod", application.startPeriod());
        item.put("endDate", application.endDate().toString());
        item.put("endPeriod", application.endPeriod());
        item.put("durationHalfDays", application.durationHalfDays());
        item.put("durationDays", application.durationDays());
        item.put("reason", application.reason());
        item.put("status", application.status());
        item.put("version", application.version());
        putTime(item, "submittedAt", application.submittedAt());
        putTime(item, "completedAt", application.completedAt());
        putTime(item, "createdAt", application.createdAt());
        putTime(item, "updatedAt", application.updatedAt());
    }

    private void putTime(ObjectNode item, String field, java.time.LocalDateTime value) {
        if (value != null) {
            item.put(field, value.toString());
        }
    }

    private Long positiveLong(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        long parsed = value.asLong(0);
        if (parsed < 1) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return parsed;
    }

    private int positiveInt(JsonNode arguments, String field, int fallback) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        int parsed = value.asInt(0);
        if (parsed < 1) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return parsed;
    }

    private String text(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
