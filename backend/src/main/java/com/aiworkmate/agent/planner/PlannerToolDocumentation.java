package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.registry.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.nio.charset.StandardCharsets;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;

/** Model-facing documentation only. Never changes executable schemas, hashes, authorization or tool availability. */
final class PlannerToolDocumentation {
    private static final Map<String, String> ARGUMENT_NOTES = Map.ofEntries(
            Map.entry("page", "列表页码，从1开始；可选时省略即使用服务端默认值。"),
            Map.entry("size", "每页条数；遵守该工具Schema上限，可选时无需追问。"),
            Map.entry("version", "当前已有记录的乐观锁版本；必须来自用户或可信页面上下文，不可编造。"),
            Map.entry("applicationId", "已有申请的真实编号，区别于审批任务编号；不可使用示例编号。"),
            Map.entry("taskId", "已有任务的真实编号，区别于申请编号；必须符合本工具声明的类型。"),
            Map.entry("leaveType", "请假类型枚举：PERSONAL事假、ANNUAL年假、SICK病假；其他类型以Schema为准。"),
            Map.entry("startDate", "开始日期YYYY-MM-DD；相对日期按系统提供的业务日期解析。"),
            Map.entry("endDate", "结束日期YYYY-MM-DD，不得早于开始日期。"),
            Map.entry("startPeriod", "开始半天：AM上午、PM下午；该契约以半天计，不支持任意小时精度。"),
            Map.entry("endPeriod", "结束半天：AM上午、PM下午；明确全天使用开始AM、结束PM。"),
            Map.entry("reason", "用户明确提供的业务理由，保留原意和原文；必填且未提供时才追问，不替用户编造。"),
            Map.entry("comment", "审批意见，按用户原文填写；同意或驳回取决于请求动作，不取决于意见语气。"),
            Map.entry("approverUserId", "可选指定审批人；未指定时省略，由业务服务解析，不得猜测人员编号。"),
            Map.entry("status", "状态筛选只使用当前工具Schema枚举；可选时无需为了筛选而追问。"));

    private PlannerToolDocumentation() { }

    static void append(ObjectMapper mapper, ObjectNode root, List<ToolDefinition> tools) {
        var notes = root.putObject("argumentNotes");
        var used = new TreeSet<String>();
        var definitions = root.putArray("allowedTools");
        for (var tool : tools) {
            tool.inputSchema().path("properties").fieldNames().forEachRemaining(used::add);
            var node = definitions.addObject();
            node.put("code", tool.code());
            node.put("name", tool.name());
            node.put("description", tool.description());
            node.put("purpose", tool.purpose());
            node.set("argumentsSchema", tool.inputSchema());
            node.set("returns", returnShape(mapper, tool.outputSchema(), 2));
            if (tool.sideEffect() != null) node.put("sideEffect", tool.sideEffect().name());
            if (tool.confirmationPolicy() != null) node.put("confirmation", tool.confirmationPolicy().name());
            var example = example(mapper, tool.code());
            if (example != null) node.set("example", example);
        }
        used.forEach(name -> { if (ARGUMENT_NOTES.containsKey(name)) notes.put(name, ARGUMENT_NOTES.get(name)); });
    }

    static String fit(ObjectMapper mapper, String serialized, int maxBytes) {
        if (size(serialized) <= maxBytes) return serialized;
        ObjectNode root;
        try { root = (ObjectNode) mapper.readTree(serialized); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException(invalid); }
        // Drop presentation in priority order. Never remove a candidate, input constraint or user context.
        for (String field : List.of("example", "argumentNotes", "returns", "description", "name")) {
            if ("argumentNotes".equals(field)) root.remove(field);
            else root.path("allowedTools").forEach(tool -> ((ObjectNode) tool).remove(field));
            serialized = root.toString();
            if (size(serialized) <= maxBytes) return serialized;
        }
        throw new BusinessException(ErrorCode.REQUEST_INVALID, "error.aiConfiguration.contextExceeded");
    }

    private static int size(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }

    private static JsonNode returnShape(ObjectMapper mapper, JsonNode schema, int depth) {
        String type = schema.path("type").asText("value");
        if (depth > 0 && "object".equals(type)) {
            var shape = mapper.createObjectNode();
            schema.path("properties").fields().forEachRemaining(field ->
                    shape.set(field.getKey(), returnShape(mapper, field.getValue(), depth - 1)));
            return shape;
        }
        if (depth > 0 && "array".equals(type))
            return mapper.createArrayNode().add(returnShape(mapper, schema.path("items"), depth));
        if (schema.has("const")) return mapper.getNodeFactory().textNode("const:" + schema.get("const"));
        if (schema.has("enum")) type += ":" + schema.get("enum");
        return mapper.getNodeFactory().textNode(type);
    }

    private static JsonNode example(ObjectMapper mapper, String code) {
        // Synthetic format examples, never real resource identifiers or a substitute for a user's arguments.
        var result = mapper.createObjectNode();
        var args = result.putObject("arguments");
        var output = result.putObject("result");
        switch (code) {
            case "leave.apply", "leave.createDraft" -> {
                args.put("leaveType", "PERSONAL").put("startDate", "2030-01-15").put("startPeriod", "AM")
                        .put("endDate", "2030-01-15").put("endPeriod", "PM").put("reason", "示例事由");
                boolean apply = "leave.apply".equals(code);
                output.put("applicationId", 73).put("status", apply ? "PENDING" : "DRAFT").put("version", apply ? 1 : 0);
                if (apply) output.put("approvalTaskId", 901);
            }
            case "leave.submit" -> {
                args.put("applicationId", 73).put("version", 2);
                output.put("applicationId", 73).put("status", "PENDING").put("version", 3);
            }
            case "todo.query" -> {
                args.put("page", 1).put("size", 20);
                output.putArray("items");
                output.put("total", 0).put("page", 1).put("size", 20).put("businessType", "ALL").put("status", "PENDING");
            }
            case "notification.mine" -> {
                args.put("page", 1).put("size", 20);
                output.putArray("items");
                output.put("total", 0).put("page", 1).put("size", 20);
            }
            default -> { return null; }
        }
        return result;
    }
}
