package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Fixed-resource search port. Returned content remains untrusted model input. */
public interface KnowledgeToolPort {
    Result search(ToolActorContext context, Query query);
    BaseQueryResult queryBases(ToolActorContext context, BaseQuery query);
    DocumentQueryResult queryDocuments(ToolActorContext context, DocumentQuery query);
    CreateBaseResult createBase(ToolActorContext context, CreateBaseCommand command);
    UpdateBaseResult updateBase(ToolActorContext context, UpdateBaseCommand command);
    CreateTextResult createText(ToolActorContext context, CreateTextCommand command);

    record Query(String text, int topK, Double minScore) { }
    record BaseQuery(Long knowledgeBaseId, int limit) { }
    record BaseQueryResult(List<BaseItem> items) {
        public BaseQueryResult { items = List.copyOf(items); }
    }
    record BaseItem(long knowledgeBaseId, String name, String icon, String description,
                    long documentCount, long chunkCount, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record DocumentQuery(long knowledgeBaseId, Long documentId, int page, int size) { }
    record DocumentQueryResult(long knowledgeBaseId, List<DocumentItem> records,
                               long total, int page, int size) {
        public DocumentQueryResult { records = List.copyOf(records); }
    }
    record DocumentItem(long documentId, String filename, long fileSize, String fileType,
                        int chunkCount, String status, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record CreateBaseCommand(String name, String icon, String description) { }
    record CreateBaseResult(long knowledgeBaseId, String name, String icon, String description,
                            long documentCount, long chunkCount, LocalDateTime createdAt)
            implements ToolWriteReceipt { }
    record UpdateBaseCommand(long kbId, String name, String icon, String description,
                             Integer chunkSize, Integer chunkOverlap, Integer denseTopK, Integer sparseTopK) { }
    record UpdateBaseResult(long knowledgeBaseId, String name, String icon, String description,
                            long documentCount, long chunkCount, int chunkSize, int chunkOverlap,
                            int denseTopK, int sparseTopK, LocalDateTime updatedAt)
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
