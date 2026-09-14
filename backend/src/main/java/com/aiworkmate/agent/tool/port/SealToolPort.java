package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

public interface SealToolPort {
    Page query(ToolActorContext context, Query query);
    enum Queue { MINE, PENDING }
    record Query(Long usageId, Queue queue, String status, int page, int size) { }
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Item(long id, String applicantName, String approverName, String sealType,
                String documentTitle, String usageReason, int copies, String status, int version,
                String taskStatus, LocalDateTime submittedAt, LocalDateTime completedAt,
                Integer actualCopies, String handlerName, LocalDateTime usedAt,
                LocalDateTime returnedAt, boolean canWithdraw, boolean canDecide,
                boolean canRegisterUse, boolean canReturn, boolean canArchiveDocument) { }
}
