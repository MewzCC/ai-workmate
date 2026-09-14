package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Stable Agent-facing query port; implementations retain domain authorization. */
public interface TodoToolPort {
    Page query(ToolActorContext context, Query query);

    record Query(String status, LocalDateTime from, LocalDateTime to, int page, int size) { }
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Item(Long id, Long applicationId, String applicantName, String leaveType,
                int durationHalfDays, String status, int version, LocalDateTime submittedAt,
                LocalDateTime dueAt, boolean overdue) { }
}
