package com.aiworkmate.dto;

/**
 * AI 回复引用的知识库片段。
 *
 * @param docId   知识库文档 ID
 * @param chunkId 分块 ID
 * @param source  来源文件名
 * @param score   检索相似度（0~1）
 * @param text    引用片段内容（可能截断）
 */
public record MessageCitationResponse(
        String docId,
        String chunkId,
        String source,
        double score,
        String text
) {
}
