package com.aiworkmate.agent.tool.port;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Stable boundary for self-owned leave reads and single atomic writes. */
public interface LeaveToolPort {
    Page mine(ToolActorContext context, Query query);
    Item getMine(ToolActorContext context, long applicationId);
    WriteResult createDraft(ToolActorContext context, Draft command, String operationKey);
    WriteResult submit(ToolActorContext context, long applicationId, int version);
    WriteResult apply(ToolActorContext context, Draft command, String operationKey);

    record Query(String status, int page, int size) { }
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Draft(String leaveType, Long approverUserId, LocalDate startDate, String startPeriod,
                 LocalDate endDate, String endPeriod, String reason) { }
    record Item(long id, String approverName, String leaveType, LocalDate startDate, String startPeriod,
                LocalDate endDate, String endPeriod, int durationHalfDays, double durationDays,
                String reason, String status, int version, LocalDateTime submittedAt,
                LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record WriteResult(long applicationId, String status, int version, Long approvalTaskId) { }
}
