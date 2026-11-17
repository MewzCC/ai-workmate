package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DocumentMetadataToolModels;
import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class HrEmployeeDocumentsQueryToolHandler extends DocumentMetadataQueryToolHandler {
    private final HrEmployeeToolPort port;
    public HrEmployeeDocumentsQueryToolHandler(HrEmployeeToolPort port, ObjectMapper json) {
        super(ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY, "employeeId", json);
        this.port = port;
    }
    @Override protected ToolPage<DocumentMetadataToolModels.Item> invoke(TrustedToolContext context, DocumentMetadataToolModels.Query query) {
        return port.documents(context.actor(), query);
    }
}
