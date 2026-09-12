package com.aiworkmate.agent.tool.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AssetToolPort {
    Page query(ToolActorContext context, Query query);
    record Query(String keyword, String category, String status, int page, int size) {}
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Item(long id, String assetCode, String name, String category, String specification,
                String status, String departmentName, String ownerName, LocalDate purchaseDate,
                BigDecimal originalValue, String remark, int version, boolean canEdit,
                boolean canDelete, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
