package com.aiworkmate.dto;

import java.util.List;

/** Validated semantic nodes, excluding raw JSON and executable extensions. */
public record ApprovalProcessDefinitionResponse(long processId, String processKey, String processName,
                                                Long formId, String status, int version,
                                                List<ApprovalProcessAgentDraftRequest.Node> nodes) {
    public ApprovalProcessDefinitionResponse { nodes = List.copyOf(nodes); }
}
