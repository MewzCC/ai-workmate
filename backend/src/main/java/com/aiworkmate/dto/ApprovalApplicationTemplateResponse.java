package com.aiworkmate.dto;

import java.util.List;

/** Applicant metadata: enabled fields and display-only process preview; no creator or target identities. */
public record ApprovalApplicationTemplateResponse(long formId, String formKey, String formName,
        String description, int version, List<ApprovalFormDefinitionResponse.Field> fields,
        Process process) {
    public ApprovalApplicationTemplateResponse { fields = List.copyOf(fields); }
    public record Process(long id, String processKey, String processName, int version, List<Node> nodes) {
        public Process { nodes = List.copyOf(nodes); }
    }
    public record Node(String nodeName, String approveType) { }
}
