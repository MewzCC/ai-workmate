package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.GenericApprovalTaskDetail;
import com.aiworkmate.dto.GenericApprovalTaskItem;
import java.time.LocalDateTime;

public interface GenericApprovalTaskService {
    int AGENT_FIELD_PAGE_SIZE = 4;
    PageResponse<GenericApprovalTaskItem> mine(Long userId, Long expectedTenantId, String status, LocalDateTime from, LocalDateTime to, int page, int size);
    GenericApprovalTaskDetail detail(Long userId, Long expectedTenantId, Long taskId, int fieldPage);
    GenericApprovalTaskDetail detailAssignedForAgent(Long userId, Long expectedTenantId, Long taskId, int fieldPage);
    GenericApprovalTaskDetail approve(Long userId, Long expectedTenantId, Long taskId, ApprovalDecisionRequest request);
    GenericApprovalTaskDetail reject(Long userId, Long expectedTenantId, Long taskId, ApprovalDecisionRequest request);
    PageResponse<com.aiworkmate.dto.ApprovalParticipantResponse> participants(Long userId,Long expectedTenantId,Long taskId,
            String purpose,com.aiworkmate.service.model.BusinessCandidateQuery query);
    com.aiworkmate.dto.GenericApprovalParticipantReceipt transfer(Long userId,Long expectedTenantId,Long taskId,com.aiworkmate.dto.ApprovalParticipantRequest request);
    com.aiworkmate.dto.GenericApprovalParticipantReceipt copyTo(Long userId,Long expectedTenantId,Long taskId,com.aiworkmate.dto.ApprovalParticipantRequest request);
}
