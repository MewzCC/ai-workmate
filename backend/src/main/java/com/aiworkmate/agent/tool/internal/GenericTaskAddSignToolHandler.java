package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.GenericApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class GenericTaskAddSignToolHandler extends TypedWriteToolHandler<ApprovalTaskToolPort.AddSignCommand,GenericApprovalTaskToolPort.AddSignResult> {
    private final GenericApprovalTaskToolPort port;
    public GenericTaskAddSignToolHandler(GenericApprovalTaskToolPort port,ObjectMapper json){super(ToolCode.APPROVAL_GENERIC_TASK_ADD_SIGN,json);this.port=port;}
    @Override protected ApprovalTaskToolPort.AddSignCommand parseArguments(JsonNode args){return new ApprovalTaskToolPort.AddSignCommand(
            BoundedToolArguments.requiredLong(args,"taskId",1),BoundedToolArguments.requiredLong(args,"targetUserId",1),
            BoundedToolArguments.requiredInt(args,"version",0,Integer.MAX_VALUE-1),BoundedToolArguments.requiredText(args,"mode"),BoundedToolArguments.requiredText(args,"reason"));}
    @Override protected GenericApprovalTaskToolPort.AddSignResult invoke(TrustedToolContext context,ApprovalTaskToolPort.AddSignCommand command){return port.addSign(context.actor(),command);}
}
