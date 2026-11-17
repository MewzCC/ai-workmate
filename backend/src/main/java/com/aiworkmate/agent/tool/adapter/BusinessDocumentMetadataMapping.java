package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.DocumentMetadataToolModels.Item;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.BusinessDocumentMetadata;

final class BusinessDocumentMetadataMapping {
    private BusinessDocumentMetadataMapping() { }
    static ToolPage<Item> page(PageResponse<BusinessDocumentMetadata> page) {
        return new ToolPage<>(page.records().stream().map(item -> new Item(item.documentId(), item.documentType(),
                item.displayName(), item.mimeType(), item.fileSize(), item.createdAt())).toList(),
                page.total(), page.page(), page.size());
    }
}
