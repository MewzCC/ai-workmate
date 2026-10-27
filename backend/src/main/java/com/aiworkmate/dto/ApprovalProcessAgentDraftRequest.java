package com.aiworkmate.dto;

import java.util.List;

/** Internal bounded process-draft command used by the controlled Agent adapter. */
public record ApprovalProcessAgentDraftRequest(
        String processKey, String processName, String description, Long formId, List<Node> nodes
) {
    public ApprovalProcessAgentDraftRequest { nodes = List.copyOf(nodes); }

    public record Node(String nodeType, String nodeName, String approveType, String targetKey,
                       String mode, Boolean timeoutEnabled, Integer timeoutHours, String timeoutAction) { }
}
