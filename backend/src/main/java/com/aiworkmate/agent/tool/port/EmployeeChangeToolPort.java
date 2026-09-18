package com.aiworkmate.agent.tool.port;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface EmployeeChangeToolPort {
    Page query(ToolActorContext context, Query query);
    ApplicationResult apply(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<ApplicationResult> findApplication(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey);

    record Query(String status, String changeType, String keyword, int page, int size) {}
    record ApplicationCommand(
            long employeeUserId, String changeType, LocalDate effectiveDate,
            Long targetDepartmentId, Long targetPositionId, Long targetSupervisorUserId,
            long reviewApproverUserId, String reason) {}
    record ApplicationResult(long changeId, String status, int version, LocalDateTime submittedAt)
            implements ToolWriteReceipt {}
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Item(long id, String employeeName, String applicantName, String reviewApproverName,
                String changeType, LocalDate effectiveDate, String currentDepartmentName,
                String currentPositionName, String targetDepartmentName, String targetPositionName,
                String targetSupervisorName, String reason, String status, int version,
                boolean canApprove, boolean canWithdraw, LocalDateTime submittedAt,
                LocalDateTime decidedAt, LocalDateTime appliedAt) {}
}
