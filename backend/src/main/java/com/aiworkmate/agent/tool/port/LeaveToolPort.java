package com.aiworkmate.agent.tool.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Stable boundary for self-owned leave reads and single atomic writes. */
public interface LeaveToolPort {
    Page mine(Long actorUserId, Query query);
    Item getMine(Long actorUserId, long applicationId);
    WriteResult createDraft(Long actorUserId, Draft command, String operationKey);
    WriteResult submit(Long actorUserId, long applicationId, int version, String taskId);
    WriteResult apply(Long actorUserId, Draft command, String operationKey);

    record Query(String status, int page, int size) { }
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Draft(String leaveType, Long approverUserId, LocalDate startDate, String startPeriod,
                 LocalDate endDate, String endPeriod, String reason) { }
    record Item(long id, String approverName, String leaveType, LocalDate startDate, String startPeriod,
                LocalDate endDate, String endPeriod, int durationHalfDays, BigDecimal durationDays,
                String reason, String status, int version, LocalDateTime submittedAt,
                LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record WriteResult(long applicationId, String status, int version, Long approvalTaskId) { }
}
