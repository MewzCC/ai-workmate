package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary for tenant data-dictionary reads and bounded single writes. */
public interface DictionaryToolPort {
    DictionaryOverview dictionaries(ToolActorContext context, DictionaryQuery query);

    CreateTypeResult createType(ToolActorContext context, CreateTypeCommand command);

    UpdateTypeResult updateType(ToolActorContext context, UpdateTypeCommand command);

    record DictionaryQuery(String keyword, String status) { }

    record DictionaryOverview(List<DictionaryType> records, boolean canManage) {
        public DictionaryOverview { records = List.copyOf(records); }
    }

    record DictionaryType(String code, String name, String description, String status, int sortOrder,
                          int itemCount, int activeItemCount, int version, LocalDateTime updatedAt) { }

    record CreateTypeCommand(String code, String name, String description, Integer sortOrder) { }

    record CreateTypeResult(long dictionaryTypeId, String code, String name, String description,
                            String status, int sortOrder, int version, LocalDateTime updatedAt)
            implements ToolWriteReceipt { }

    record UpdateTypeCommand(String code, int version, String name, String description, Integer sortOrder) { }

    record UpdateTypeResult(long dictionaryTypeId, String code, String name, String description,
                            String status, int sortOrder, int version, LocalDateTime updatedAt)
            implements ToolWriteReceipt { }
}
