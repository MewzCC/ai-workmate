package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.fasterxml.jackson.databind.JsonNode;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalPositiveLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

final class LeaveDraftArguments {
    private LeaveDraftArguments() { }

    static LeaveToolPort.Draft parse(JsonNode arguments) {
        return new LeaveToolPort.Draft(
                requiredText(arguments, "leaveType"), optionalPositiveLong(arguments, "approverUserId"),
                requiredDate(arguments, "startDate"), requiredText(arguments, "startPeriod"),
                requiredDate(arguments, "endDate"), requiredText(arguments, "endPeriod"),
                requiredText(arguments, "reason"));
    }
}
