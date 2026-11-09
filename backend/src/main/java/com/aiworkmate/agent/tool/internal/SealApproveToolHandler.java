package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.WorkflowDecisionToolModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class SealApproveToolHandler extends AssignedWorkflowDecisionToolHandler {
    private final SealToolPort port;
    public SealApproveToolHandler(SealToolPort port,ObjectMapper mapper){super(ToolCode.SEAL_APPROVE,mapper,false);this.port=port;}
    @Override protected WorkflowDecisionToolModels.Receipt invoke(TrustedToolContext context,WorkflowDecisionToolModels.Command command){
        return port.approve(context.actor(),command);
    }
}
