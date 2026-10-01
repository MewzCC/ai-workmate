package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Fixed-resource search port. Returned content remains untrusted model input. */
public interface KnowledgeToolPort {
    Result search(ToolActorContext context, Query query);
    CreateBaseResult createBase(ToolActorContext context, CreateBaseCommand command);
    CreateTextResult createText(ToolActorContext context, CreateTextCommand command);

    record Query(String text, int topK, Double minScore) { }
    record CreateBaseCommand(String name, String icon, String description) { }
    record CreateBaseResult(long knowledgeBaseId, String name, String icon, String description,
                            long documentCount, long chunkCount, LocalDateTime createdAt)
            implements ToolWriteReceipt { }
    record CreateTextCommand(long kbId, String filename, String content) { }
    record CreateTextResult(long documentId, long kbId, String filename, String status,
                            int chunkCount, LocalDateTime createdAt) implements ToolWriteReceipt { }
    record Result(List<Item> items) {
        public Result { items = List.copyOf(items); }
    }
    record Item(String content, double score, String matchType, long documentId,
                long chunkId, String filename, int chunkIndex) { }
}
