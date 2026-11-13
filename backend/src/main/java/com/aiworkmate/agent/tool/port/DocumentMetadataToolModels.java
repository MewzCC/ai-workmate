package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;

/** Shared values only; callers cannot select a domain, storage system or URL. */
public final class DocumentMetadataToolModels {
    private DocumentMetadataToolModels() { }
    public record Query(long resourceId, int page, int size) { }
    public record Item(String documentId, String documentType, String displayName, String mimeType,
                       long fileSize, LocalDateTime createdAt) { }
}
