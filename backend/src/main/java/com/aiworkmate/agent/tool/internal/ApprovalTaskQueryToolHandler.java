package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageNumber;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageSize;

@Component
public final class ApprovalTaskQueryToolHandler
        extends TypedReadToolHandler<ApprovalTaskToolPort.Query, ApprovalTaskToolPort.Page> {
    private final ApprovalTaskToolPort approvalTaskToolPort;

    public ApprovalTaskQueryToolHandler(ApprovalTaskToolPort approvalTaskToolPort, ObjectMapper objectMapper) {
        super(ToolCode.APPROVAL_TASK_QUERY, objectMapper);
        this.approvalTaskToolPort = approvalTaskToolPort;
    }

    @Override protected ApprovalTaskToolPort.Query parseArguments(JsonNode arguments) {
        LocalDateTime from = optionalDateTime(arguments, "from");
        LocalDateTime to = optionalDateTime(arguments, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new ApprovalTaskToolPort.Query(
                optionalText(arguments, "status"), from, to,
                optionalText(arguments, "keyword"), optionalText(arguments, "leaveType"),
                pageNumber(arguments),
                pageSize(arguments));
    }

    @Override protected ApprovalTaskToolPort.Page invoke(
            TrustedToolContext context, ApprovalTaskToolPort.Query query) {
        return approvalTaskToolPort.query(context.actor(), query);
    }

}
