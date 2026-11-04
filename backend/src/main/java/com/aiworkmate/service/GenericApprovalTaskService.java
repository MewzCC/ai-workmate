package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.GenericApprovalTaskDetail;
import com.aiworkmate.dto.GenericApprovalTaskItem;
import java.time.LocalDateTime;

public interface GenericApprovalTaskService {
    PageResponse<GenericApprovalTaskItem> mine(Long userId, Long expectedTenantId, String status, LocalDateTime from, LocalDateTime to, int page, int size);
    GenericApprovalTaskDetail detail(Long userId, Long expectedTenantId, Long taskId, int fieldPage);
    GenericApprovalTaskDetail approve(Long userId, Long expectedTenantId, Long taskId, ApprovalDecisionRequest request);
    GenericApprovalTaskDetail reject(Long userId, Long expectedTenantId, Long taskId, ApprovalDecisionRequest request);
}
