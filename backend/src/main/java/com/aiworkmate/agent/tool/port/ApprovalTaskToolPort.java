package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Tenant-scoped approval-center read boundary; domain service enforces live data scope. */
public interface ApprovalTaskToolPort {
    Page query(ToolActorContext context, Query query);

    record Query(String status, LocalDateTime from, LocalDateTime to, String keyword,
                 String leaveType, int page, int size) { }

    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }

    record Item(long applicationId, Long taskId, String applicantName, String approverName,
                String leaveType, double durationDays, String status, int version,
                LocalDateTime submittedAt, LocalDateTime dueAt, boolean overdue) { }
}
