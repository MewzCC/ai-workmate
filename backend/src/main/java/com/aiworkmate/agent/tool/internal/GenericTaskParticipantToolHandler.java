package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort.ParticipantCommand;
import com.aiworkmate.agent.tool.port.GenericApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

abstract class GenericTaskParticipantToolHandler extends TypedWriteToolHandler<ParticipantCommand,GenericApprovalTaskToolPort.ParticipantResult> {
    protected final GenericApprovalTaskToolPort port;
    GenericTaskParticipantToolHandler(ToolCode code,GenericApprovalTaskToolPort port,ObjectMapper json){super(code,json);this.port=port;}
    @Override protected ParticipantCommand parseArguments(JsonNode args){return new ParticipantCommand(
            BoundedToolArguments.requiredLong(args,"taskId",1),BoundedToolArguments.requiredLong(args,"targetUserId",1),
            BoundedToolArguments.requiredInt(args,"version",0,Integer.MAX_VALUE-1),BoundedToolArguments.requiredText(args,"reason"));}
}
