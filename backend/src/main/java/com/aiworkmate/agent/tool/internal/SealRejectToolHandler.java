package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.WorkflowDecisionToolModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class SealRejectToolHandler extends AssignedWorkflowDecisionToolHandler {
    private final SealToolPort port;
    public SealRejectToolHandler(SealToolPort port,ObjectMapper mapper){super(ToolCode.SEAL_REJECT,mapper,true);this.port=port;}
    @Override protected WorkflowDecisionToolModels.Receipt invoke(TrustedToolContext context,WorkflowDecisionToolModels.Command command){
        return port.reject(context.actor(),command);
    }
}
