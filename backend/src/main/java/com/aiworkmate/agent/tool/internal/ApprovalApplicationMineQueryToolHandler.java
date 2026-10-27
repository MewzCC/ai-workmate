package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public final class ApprovalApplicationMineQueryToolHandler extends
        TypedReadToolHandler<ApprovalApplicationToolPort.Query, ToolPage<ApprovalApplicationToolPort.Summary>> {
    private static final Set<String> STATUSES = Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED");
    private final ApprovalApplicationToolPort port;

    public ApprovalApplicationMineQueryToolHandler(ApprovalApplicationToolPort port, ObjectMapper mapper) {
        super(ToolCode.APPROVAL_APPLICATION_MINE_QUERY, mapper);
        this.port = port;
    }

    @Override protected ApprovalApplicationToolPort.Query parseArguments(JsonNode args) {
        String status = BoundedToolArguments.optionalText(args, "status");
        String formKey = BoundedToolArguments.optionalText(args, "formKey");
        if ((args.has("status") && (status == null || !STATUSES.contains(status)))
                || (args.has("formKey") && (formKey == null || formKey.length() > 120))) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new ApprovalApplicationToolPort.Query(status, formKey,
                BoundedToolArguments.pageNumber(args), BoundedToolArguments.pageSize(args));
    }

    @Override protected ToolPage<ApprovalApplicationToolPort.Summary> invoke(
            TrustedToolContext context, ApprovalApplicationToolPort.Query query) {
        return port.mine(context.actor(), query);
    }
}
