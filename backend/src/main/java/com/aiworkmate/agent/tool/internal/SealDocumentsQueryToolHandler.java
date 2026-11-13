package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DocumentMetadataToolModels;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class SealDocumentsQueryToolHandler extends DocumentMetadataQueryToolHandler {
    private final SealToolPort port;
    public SealDocumentsQueryToolHandler(SealToolPort port, ObjectMapper json) {
        super(ToolCode.SEAL_DOCUMENTS_QUERY, "usageId", json);
        this.port = port;
    }
    @Override protected ToolPage<DocumentMetadataToolModels.Item> invoke(TrustedToolContext context, DocumentMetadataToolModels.Query query) {
        return port.documents(context.actor(), query);
    }
}
