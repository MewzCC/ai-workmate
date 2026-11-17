package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record GenericApprovalTaskItem(Long id, Long applicationId, Long applicantUserId, String applicantName,
        String formName, String title, String nodeName, Integer nodeIndex, String status, int version,
        LocalDateTime submittedAt, LocalDateTime dueAt, boolean overdue, boolean hasSnapshot,Long parentTaskId,String addSignMode) { }
