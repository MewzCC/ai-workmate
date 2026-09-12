package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
public final class LeaveMineToolHandler
        extends TypedReadToolHandler<LeaveMineToolHandler.Query, LeaveToolPort.Page> {
    private static final int MAX_SIZE = 50;

    private final LeaveToolPort leaveToolPort;

    public LeaveMineToolHandler(LeaveToolPort leaveToolPort, ObjectMapper objectMapper) {
        super(ToolCode.LEAVE_MINE, objectMapper);
        this.leaveToolPort = leaveToolPort;
    }

    @Override protected Query parseArguments(JsonNode arguments) {
        Long applicationId = optionalPositiveLong(arguments, "applicationId");
        String status = optionalText(arguments, "status");
        boolean hasListArguments = status != null || arguments.has("page") || arguments.has("size");
        if (applicationId != null && hasListArguments) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new Query(applicationId, status,
                positiveInt(arguments, "page", 1, Integer.MAX_VALUE),
                positiveInt(arguments, "size", 20, MAX_SIZE));
    }

    @Override protected LeaveToolPort.Page invoke(TrustedToolContext context, Query query) {
        if (query.applicationId() != null) {
            return new LeaveToolPort.Page(
                    List.of(leaveToolPort.getMine(context.actor(), query.applicationId())),
                    1, 1, 1);
        }
        return leaveToolPort.mine(context.actor(),
                new LeaveToolPort.Query(query.status(), query.page(), query.size()));
    }

    record Query(Long applicationId, String status, int page, int size) { }
}
