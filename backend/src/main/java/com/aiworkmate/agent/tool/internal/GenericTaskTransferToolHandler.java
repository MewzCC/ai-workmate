package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort.ParticipantCommand;
import com.aiworkmate.agent.tool.port.GenericApprovalTaskToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class GenericTaskTransferToolHandler extends GenericTaskParticipantToolHandler {
    public GenericTaskTransferToolHandler(GenericApprovalTaskToolPort port,ObjectMapper json){super(ToolCode.APPROVAL_GENERIC_TASK_TRANSFER,port,json);}
    @Override protected GenericApprovalTaskToolPort.ParticipantResult invoke(TrustedToolContext context,ParticipantCommand command){return port.transfer(context.actor(),command);}
}
