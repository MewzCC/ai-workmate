package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredEnum;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;

@Component
public final class AttendanceReissueDecideToolHandler
        extends TypedWriteToolHandler<AttendanceToolPort.ReissueDecisionCommand,
        AttendanceToolPort.ReissueDecisionResult> {
    private final AttendanceToolPort port;

    public AttendanceReissueDecideToolHandler(AttendanceToolPort port, ObjectMapper objectMapper) {
        super(ToolCode.ATTENDANCE_REISSUE_DECIDE, objectMapper);
        this.port = port;
    }

    @Override
    protected AttendanceToolPort.ReissueDecisionCommand parseArguments(JsonNode arguments) {
        String decision = requiredEnum(arguments, "decision", Decision.class).name();
        String comment = optionalText(arguments, "comment");
        if ((comment != null && comment.length() > 500)
                || ("REJECTED".equals(decision) && comment == null)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new AttendanceToolPort.ReissueDecisionCommand(
                requiredLong(arguments, "reissueId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE), decision, comment);
    }

    @Override
    protected AttendanceToolPort.ReissueDecisionResult invoke(
            TrustedToolContext context, AttendanceToolPort.ReissueDecisionCommand command) {
        return port.decideReissue(context.actor(), command);
    }

    private enum Decision { APPROVED, REJECTED }
}
