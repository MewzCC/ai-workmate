package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Fixed-resource search port. Returned content remains untrusted model input. */
public interface KnowledgeToolPort {
    Result search(Long actorUserId, Query query);

    record Query(String text, int topK, Double minScore) { }
    record Result(List<Item> items) {
        public Result { items = List.copyOf(items); }
    }
    record Item(String content, double score, String matchType, long documentId,
                long chunkId, String filename, int chunkIndex) { }
}
