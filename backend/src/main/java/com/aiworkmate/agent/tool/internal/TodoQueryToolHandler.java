package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
@RequiredArgsConstructor
public final class TodoQueryToolHandler implements ToolHandler {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final TodoToolPort todoToolPort;
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
        LocalDateTime from = optionalDateTime(arguments, "from");
        LocalDateTime to = optionalDateTime(arguments, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        int page = positiveInt(arguments, "page", DEFAULT_PAGE, Integer.MAX_VALUE);
        int size = positiveInt(arguments, "size", DEFAULT_SIZE, MAX_SIZE);
        String status = optionalText(arguments, "status");

        TodoToolPort.Page result = todoToolPort.query(
                context.userId(), new TodoToolPort.Query(status, from, to, page, size));
        ObjectNode output = objectMapper.createObjectNode();
        ArrayNode items = output.putArray("items");
        result.items().forEach(todo -> appendTodo(items, todo));
        output.put("total", result.total());
        output.put("page", result.page());
        output.put("size", result.size());
        return output;
    }

    private void appendTodo(ArrayNode items, TodoToolPort.Item todo) {
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

}
