package com.aiworkmate.agent.tool.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AssetToolPort {
    Page query(ToolActorContext context, Query query);
    ClaimResult claim(ToolActorContext context, ClaimCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<ClaimResult> findClaim(
            ToolActorContext context, ClaimCommand command, ToolOperationKey operationKey);
    ReturnResult returnAsset(ToolActorContext context, ReturnCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<ReturnResult> findReturn(
            ToolActorContext context, ReturnCommand command, ToolOperationKey operationKey);
    RepairStartResult startRepair(
            ToolActorContext context, RepairStartCommand command, ToolOperationKey operationKey);
    ToolWriteVerification<RepairStartResult> findRepairStart(
            ToolActorContext context, RepairStartCommand command, ToolOperationKey operationKey);
    record Query(String keyword, String category, String status, int page, int size) {}
    record ClaimCommand(long assetId, long employeeId, int version, String reason) {}
    record ClaimResult(long assetId, String status, int version) implements ToolWriteReceipt {}
    record ReturnCommand(long assetId, int version, String reason) {}
    record ReturnResult(long assetId, String status, int version) implements ToolWriteReceipt {}
    record RepairStartCommand(long assetId, int version, String reason) {}
    record RepairStartResult(long assetId, String status, int version) implements ToolWriteReceipt {}
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Item(long id, String assetCode, String name, String category, String specification,
                String status, String departmentName, String ownerName, LocalDate purchaseDate,
                BigDecimal originalValue, String remark, int version, boolean canEdit,
                boolean canDelete, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
