package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

public interface SealToolPort {
    Page query(ToolActorContext context, Query query);
    ApplicationResult apply(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<ApplicationResult> findApplication(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey);
    enum Queue { MINE, PENDING }
    record Query(Long usageId, Queue queue, String status, int page, int size) { }
    record ApplicationCommand(String sealType, String documentTitle, String usageReason, int copies) { }
    record ApplicationResult(long usageId, String status, int version, LocalDateTime submittedAt)
            implements ToolWriteReceipt { }
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
