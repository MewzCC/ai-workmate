package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitorToolPort {
    Page query(ToolActorContext context, Query query);
    enum Queue { MINE, PENDING }
    record Query(Long bookingId, Queue queue, String status, int page, int size) { }
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Item(long id, String applicantName, String approverName, String hostName,
                String visitorName, String visitorCompany, String purpose,
                LocalDateTime expectedVisitAt, LocalDateTime expectedLeaveAt, Integer partySize,
                String status, int version, String taskStatus, LocalDateTime submittedAt,
                LocalDateTime completedAt, String registeredByName, LocalDateTime checkedInAt,
                LocalDateTime visitedAt, LocalDateTime leftAt, LocalDateTime noShowAt,
                boolean canWithdraw, boolean canDecide, boolean canCheckIn,
                boolean canMarkVisited, boolean canLeave, boolean canMarkNoShow) { }
}
