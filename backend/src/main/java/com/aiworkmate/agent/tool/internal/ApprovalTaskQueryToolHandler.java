package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDateTime;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
@RequiredArgsConstructor
public final class ApprovalTaskQueryToolHandler implements ToolHandler {
    private final ApprovalTaskToolPort approvalTaskToolPort;
    private final ObjectMapper objectMapper;

    @Override public String toolCode() { return "approval.task.query"; }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        LocalDateTime from = optionalDateTime(arguments, "from");
        LocalDateTime to = optionalDateTime(arguments, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        var query = new ApprovalTaskToolPort.Query(
                optionalText(arguments, "status"), from, to,
                optionalText(arguments, "keyword"), optionalText(arguments, "leaveType"),
                positiveInt(arguments, "page", 1, 10000),
                positiveInt(arguments, "size", 20, 50));
        var result = approvalTaskToolPort.query(context.userId(), query);
        ObjectNode output = objectMapper.createObjectNode();
        var items = output.putArray("items");
        result.items().forEach(record -> {
            ObjectNode item = items.addObject();
            item.put("applicationId", record.applicationId());
            if (record.taskId() != null) item.put("taskId", record.taskId());
            item.put("applicantName", record.applicantName());
            if (record.approverName() != null) item.put("approverName", record.approverName());
            item.put("leaveType", record.leaveType());
            item.put("durationDays", record.durationDays());
            item.put("status", record.status());
            item.put("version", record.version());
            if (record.submittedAt() != null) item.put("submittedAt", record.submittedAt().toString());
            if (record.dueAt() != null) item.put("dueAt", record.dueAt().toString());
            item.put("overdue", record.overdue());
        });
        output.put("total", result.total());
        output.put("page", result.page());
        output.put("size", result.size());
        return output;
    }
}
