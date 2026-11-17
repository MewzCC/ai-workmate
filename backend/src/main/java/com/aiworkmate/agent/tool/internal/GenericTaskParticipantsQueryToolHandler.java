package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.GenericApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class GenericTaskParticipantsQueryToolHandler extends TypedReadToolHandler<GenericApprovalTaskToolPort.ParticipantQuery,ApprovalTaskToolPort.Participants> {
    private final GenericApprovalTaskToolPort port;
    public GenericTaskParticipantsQueryToolHandler(GenericApprovalTaskToolPort port,ObjectMapper json){super(ToolCode.APPROVAL_GENERIC_TASK_PARTICIPANTS_QUERY,json);this.port=port;}
    @Override protected GenericApprovalTaskToolPort.ParticipantQuery parseArguments(JsonNode args){return new GenericApprovalTaskToolPort.ParticipantQuery(
            BoundedToolArguments.requiredLong(args,"taskId",1),BoundedToolArguments.requiredText(args,"purpose"),BoundedToolArguments.optionalText(args,"keyword"),
            args.has("page")?BoundedToolArguments.requiredInt(args,"page",1,10000):1,args.has("size")?BoundedToolArguments.requiredInt(args,"size",1,50):20);}
    @Override protected ApprovalTaskToolPort.Participants invoke(TrustedToolContext context,GenericApprovalTaskToolPort.ParticipantQuery query){return port.participants(context.actor(),query);}
}
