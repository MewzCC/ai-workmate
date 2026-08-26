package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.TodoResponse;
import com.aiworkmate.service.LeaveWorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public final class TodoQueryToolHandler implements ToolHandler {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final LeaveWorkflowService leaveWorkflowService;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() {
        return "todo.query";
    }

    @Override
    public String handlerVersion() {
        return "1.0.0";
    }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        LocalDateTime from = parseDateTime(arguments, "from");
        LocalDateTime to = parseDateTime(arguments, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        int page = positiveInt(arguments, "page", DEFAULT_PAGE);
        int size = Math.min(MAX_SIZE, positiveInt(arguments, "size", DEFAULT_SIZE));
        String status = text(arguments, "status");

        PageResponse<TodoResponse> result = leaveWorkflowService.todos(
                context.userId(), status, from, to, page, size);
        ObjectNode output = objectMapper.createObjectNode();
        ArrayNode items = output.putArray("items");
        result.records().forEach(todo -> appendTodo(items, todo));
        output.put("total", result.total());
        output.put("page", result.page());
        output.put("size", result.size());
        return output;
    }

    private void appendTodo(ArrayNode items, TodoResponse todo) {
        ObjectNode item = items.addObject();
        item.put("id", todo.id());
        item.put("applicationId", todo.applicationId());
        item.put("applicantName", todo.applicantName());
        item.put("leaveType", todo.leaveType());
        item.put("durationHalfDays", todo.durationHalfDays());
        item.put("status", todo.status());
        item.put("version", todo.version());
        if (todo.submittedAt() != null) {
            item.put("submittedAt", todo.submittedAt().toString());
        }
        if (todo.dueAt() != null) {
            item.put("dueAt", todo.dueAt().toString());
        }
        item.put("overdue", todo.overdue());
    }

    private LocalDateTime parseDateTime(JsonNode arguments, String field) {
        String value = text(arguments, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
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
