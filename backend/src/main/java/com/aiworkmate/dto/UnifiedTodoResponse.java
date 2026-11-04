package com.aiworkmate.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/** Shared self-assigned task summary. No application payload, workflow configuration or storage path. */
public record UnifiedTodoResponse(Long id, Long applicationId, Long applicantUserId, String applicantName,
        String leaveType, Integer durationHalfDays, String status, int version, LocalDateTime submittedAt,
        LocalDateTime dueAt, boolean overdue, @JsonIgnore String applicantAvatar,
        @JsonIgnore LocalDateTime applicantUpdatedAt, String applicantAvatarUrl,
        String businessType, String title, String nodeName,boolean detailAvailable) {
    public UnifiedTodoResponse withAvatarUrl(String url) {
        return new UnifiedTodoResponse(id,applicationId,applicantUserId,applicantName,leaveType,durationHalfDays,
                status,version,submittedAt,dueAt,overdue,applicantAvatar,applicantUpdatedAt,url,businessType,title,nodeName,detailAvailable);
    }
}
