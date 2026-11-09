package com.aiworkmate.dto;

import java.util.List;

public record GenericApprovalTaskDetail(GenericApprovalTaskItem task, String instanceStatus, String applicationStatus,
        List<ApprovalApplicationAgentDetailResponse.Field> fields, int totalFields, int fieldPage,
        boolean canApprove, boolean canReject, String blockedReason, List<WorkflowTimelineResponse> timeline,
        boolean canTransfer, boolean canCopy) { }
