package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary for tenant data-dictionary reads and bounded single writes. */
public interface DictionaryToolPort {
    DictionaryOverview dictionaries(ToolActorContext context, DictionaryQuery query);

    DictionaryItems dictionaryItems(ToolActorContext context, DictionaryItemQuery query);

    CreateTypeResult createType(ToolActorContext context, CreateTypeCommand command);

    UpdateTypeResult updateType(ToolActorContext context, UpdateTypeCommand command);

    CreateItemResult createItem(ToolActorContext context, CreateItemCommand command);

    record DictionaryQuery(String keyword, String status) { }

    record DictionaryOverview(List<DictionaryType> records, boolean canManage) {
        public DictionaryOverview { records = List.copyOf(records); }
    }

    record DictionaryType(String code, String name, String description, String status, int sortOrder,
                          int itemCount, int activeItemCount, int version, LocalDateTime updatedAt) { }

    record DictionaryItemQuery(String typeCode, String keyword, String status, int page, int size) { }

    record DictionaryItems(String typeCode, List<DictionaryItem> records, long total,
                           int page, int size, boolean canManage) {
        public DictionaryItems { records = List.copyOf(records); }
    }

    record DictionaryItem(String value, String label, String description, String status,
                          int sortOrder, long usageCount, int version, LocalDateTime updatedAt) { }

    record CreateTypeCommand(String code, String name, String description, Integer sortOrder) { }

    record CreateTypeResult(long dictionaryTypeId, String code, String name, String description,
                            String status, int sortOrder, int version, LocalDateTime updatedAt)
            implements ToolWriteReceipt { }

    record UpdateTypeCommand(String code, int version, String name, String description, Integer sortOrder) { }

    record UpdateTypeResult(long dictionaryTypeId, String code, String name, String description,
                            String status, int sortOrder, int version, LocalDateTime updatedAt)
            implements ToolWriteReceipt { }

    record CreateItemCommand(String typeCode, String value, String label,
                             String description, Integer sortOrder) { }

    record CreateItemResult(long dictionaryItemId, String typeCode, String value, String label,
                            String description, String status, int sortOrder, long usageCount,
                            int version, LocalDateTime updatedAt) implements ToolWriteReceipt { }
}
