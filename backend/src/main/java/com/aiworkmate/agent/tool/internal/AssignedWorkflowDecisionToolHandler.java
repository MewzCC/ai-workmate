package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.WorkflowDecisionToolModels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared bounded coordinates only; subclasses bind one fixed domain operation. */
abstract class AssignedWorkflowDecisionToolHandler
        extends TypedWriteToolHandler<WorkflowDecisionToolModels.Command,WorkflowDecisionToolModels.Receipt> {
    private final boolean reasonRequired;
    protected AssignedWorkflowDecisionToolHandler(ToolCode code,ObjectMapper mapper,boolean reasonRequired) {
        super(code,mapper);this.reasonRequired=reasonRequired;
    }
    @Override protected final WorkflowDecisionToolModels.Command parseArguments(JsonNode args) {
        return new WorkflowDecisionToolModels.Command(BoundedToolArguments.requiredLong(args,"taskId",1),
                BoundedToolArguments.requiredInt(args,"version",0,Integer.MAX_VALUE-1),
                reasonRequired?BoundedToolArguments.requiredText(args,"comment"):BoundedToolArguments.optionalText(args,"comment"));
    }
}
