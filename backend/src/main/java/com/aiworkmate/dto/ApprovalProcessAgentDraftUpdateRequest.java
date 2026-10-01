package com.aiworkmate.dto;

import java.util.List;

/** Internal bounded process-draft update used by the controlled Agent adapter. */
public record ApprovalProcessAgentDraftUpdateRequest(
        Integer version, String processName, String description, Long formId, List<Node> nodes
) {
    public ApprovalProcessAgentDraftUpdateRequest { nodes = List.copyOf(nodes); }

    public record Node(String nodeType, String nodeName, String approveType, String targetKey,
                       String mode, Boolean timeoutEnabled, Integer timeoutHours, String timeoutAction) { }
}
