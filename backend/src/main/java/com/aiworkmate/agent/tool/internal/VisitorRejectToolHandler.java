package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.aiworkmate.agent.tool.port.WorkflowDecisionToolModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class VisitorRejectToolHandler extends AssignedWorkflowDecisionToolHandler {
    private final VisitorToolPort port;
    public VisitorRejectToolHandler(VisitorToolPort port,ObjectMapper mapper){super(ToolCode.VISITOR_REJECT,mapper,true);this.port=port;}
    @Override protected WorkflowDecisionToolModels.Receipt invoke(TrustedToolContext context,WorkflowDecisionToolModels.Command command){
        return port.reject(context.actor(),command);
    }
}
