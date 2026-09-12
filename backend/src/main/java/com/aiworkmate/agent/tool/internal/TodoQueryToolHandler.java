package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class TodoQueryToolHandler extends TypedReadToolHandler<TodoToolPort.Query, TodoToolPort.Page> {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final TodoToolPort todoToolPort;

    public TodoQueryToolHandler(TodoToolPort todoToolPort, ObjectMapper objectMapper) {
        super(ToolCode.TODO_QUERY, objectMapper);
        this.todoToolPort = todoToolPort;
    }

    @Override protected TodoToolPort.Query parseArguments(JsonNode arguments) {
        LocalDateTime from = optionalDateTime(arguments, "from");
        LocalDateTime to = optionalDateTime(arguments, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        int page = positiveInt(arguments, "page", DEFAULT_PAGE, Integer.MAX_VALUE);
        int size = positiveInt(arguments, "size", DEFAULT_SIZE, MAX_SIZE);
        String status = optionalText(arguments, "status");

        return new TodoToolPort.Query(status, from, to, page, size);
    }

    @Override protected TodoToolPort.Page invoke(TrustedToolContext context, TodoToolPort.Query query) {
        return todoToolPort.query(context.userId(), query);
    }

}
