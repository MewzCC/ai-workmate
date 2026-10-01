package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

/** Shared closed-world parser for semantic approval process nodes. */
final class ApprovalProcessDraftArguments {
    private static final Set<String> TYPES = Set.of("START", "APPROVAL", "CONDITION", "CC", "DELAY", "END");
    private static final Set<String> APPROVERS = Set.of("DIRECT_MANAGER", "ROLE", "DEPARTMENT", "USER", "SELF", "MULTI_LEVEL");
    private static final Set<String> MODES = Set.of("COUNTERSIGN", "OR_SIGN", "SEQUENTIAL");
    private static final Set<String> TIMEOUT_ACTIONS = Set.of("REMIND", "TRANSFER", "AUTO_APPROVE");

    private ApprovalProcessDraftArguments() { }

    static List<ApprovalConfigurationToolPort.ProcessNode> nodes(JsonNode arguments) {
        JsonNode values = arguments.get("nodes");
        if (values == null || !values.isArray() || values.size() < 3 || values.size() > 20) throw invalid();
        List<ApprovalConfigurationToolPort.ProcessNode> nodes = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            String type = requiredText(value, "nodeType");
            String approver = optionalText(value, "approveType");
            String mode = optionalText(value, "mode");
            String timeoutAction = optionalText(value, "timeoutAction");
            if (!TYPES.contains(type) || approver != null && !APPROVERS.contains(approver)
                    || mode != null && !MODES.contains(mode)
                    || timeoutAction != null && !TIMEOUT_ACTIONS.contains(timeoutAction)) throw invalid();
            nodes.add(new ApprovalConfigurationToolPort.ProcessNode(type, requiredText(value, "nodeName"),
                    approver, optionalTextPreservingEmpty(value, "targetKey"), mode,
                    optionalBoolean(value, "timeoutEnabled"), optionalInt(value, "timeoutHours", 1, 720),
                    timeoutAction));
        }
        return List.copyOf(nodes);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.REQUEST_INVALID);
    }
}
