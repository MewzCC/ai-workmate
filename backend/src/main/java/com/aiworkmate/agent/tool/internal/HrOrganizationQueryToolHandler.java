package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.HrOrganizationToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.positiveInt;

@Component
@RequiredArgsConstructor
public final class HrOrganizationQueryToolHandler implements ToolHandler {
    private final HrOrganizationToolPort port;
    private final ObjectMapper objectMapper;

    @Override public String toolCode() { return ToolCode.HR_ORGANIZATION_QUERY.code(); }
    @Override public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        var result = port.query(context.userId(), new HrOrganizationToolPort.Query(
                optionalText(arguments, "keyword"), positiveInt(arguments, "limit", 50, 50)));
        ObjectNode output = objectMapper.createObjectNode();
        var departments = output.putArray("departments");
        result.departments().forEach(record -> {
            var item = departments.addObject();
            item.put("id", record.id()); item.put("code", record.code()); item.put("name", record.name());
            if (record.parentId() != null) item.put("parentId", record.parentId());
            item.put("status", record.status());
        });
        var positions = output.putArray("positions");
        result.positions().forEach(record -> positions.addObject()
                .put("id", record.id()).put("code", record.code()).put("name", record.name())
                .put("status", record.status()));
        var employees = output.putArray("employees");
        result.employees().forEach(record -> {
            var item = employees.addObject();
            item.put("id", record.id()); item.put("name", record.name()); item.put("role", record.role());
            item.put("status", record.status());
            if (record.departmentId() != null) item.put("departmentId", record.departmentId());
            if (record.positionId() != null) item.put("positionId", record.positionId());
            if (record.approverName() != null) item.put("approverName", record.approverName());
        });
        return output;
    }
}
