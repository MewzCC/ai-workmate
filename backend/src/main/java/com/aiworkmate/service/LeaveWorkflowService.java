package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.ApprovalStatusCountResponse;
import com.aiworkmate.dto.ApproverCandidateResponse;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApprovalContextResponse;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.TodoResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.dto.WorkflowTimelineResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface LeaveWorkflowService {

    LeaveApprovalContextResponse approvalContext(Long userId);

    PageResponse<ApproverCandidateResponse> approverCandidates(Long userId, String keyword, int page, int size);

    LeaveApplicationResponse createDraft(Long userId, LeaveApplicationRequest request);

    LeaveApplicationResponse createAgentDraft(Long userId, LeaveApplicationRequest request,
                                              String operationKey);

    LeaveApplicationResponse updateDraft(Long userId, Long id, LeaveApplicationRequest request);

    LeaveApplicationResponse getApplication(Long userId, Long id);

    LeaveApplicationResponse getMine(Long userId, Long id);

    PageResponse<LeaveApplicationResponse> mine(Long userId, String status, int page, int size);

    PageResponse<LeaveApplicationResponse> adminList(Long userId, String status, LocalDateTime from,
                                                     LocalDateTime to, String keyword, String leaveType,
                                                     int page, int size);

    List<ApprovalStatusCountResponse> adminStats(Long userId);

    LeaveApplicationResponse submit(Long userId, Long id, VersionRequest request);

    LeaveApplicationResponse submitAgent(Long userId, Long id, VersionRequest request,
                                         Long agentTaskId);

    LeaveApplicationResponse withdraw(Long userId, Long id, VersionRequest request);

    PageResponse<TodoResponse> todos(Long userId, String status, LocalDateTime from,
                                     LocalDateTime to, int page, int size);

    LeaveApplicationResponse todoDetail(Long userId, Long taskId);

    LeaveApplicationResponse approve(Long userId, Long taskId, ApprovalDecisionRequest request);

    LeaveApplicationResponse reject(Long userId, Long taskId, ApprovalDecisionRequest request);

    List<WorkflowTimelineResponse> timeline(Long userId, Long taskId);
}
