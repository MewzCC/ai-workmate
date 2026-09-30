package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.MessageUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Deterministic, bounded presentation of already-authorized tool output. Never feeds a planner. */
public final class AgentReadResultPresenter {
    private static final int MAX_ITEMS = 20;
    private static final int MAX_FIELDS = 8;
    private static final int MAX_LENGTH = 1000;

    private AgentReadResultPresenter() { }

    public static String present(String toolCode, JsonNode result) {
        if ("todo.query".equals(toolCode)) return todo(result);
        if (toolCode == null || toolCode.isBlank() || result == null || (!result.isObject() && !result.isArray())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        StringBuilder answer = new StringBuilder(MessageUtils.resolve("agent.result.completed", plain(toolCode)));
        JsonNode items = result.isObject() ? result.path("items") : result;
        if (result.isObject() && result.path("total").canConvertToLong()) {
            answer.append('\n').append(MessageUtils.resolve("agent.result.total", result.path("total").asLong()));
        }
        if (items.isArray()) {
            if (items.isEmpty()) answer.append('\n').append(MessageUtils.resolve("agent.result.empty"));
            int shown = 0;
            for (JsonNode item : items) {
                if (shown >= MAX_ITEMS || answer.length() >= MAX_LENGTH) break;
                shown++;
                answer.append('\n').append("- ").append(shown).append(". ").append(describe(item));
            }
            if (items.size() > shown) {
                answer.append('\n').append(MessageUtils.resolve("agent.result.more", items.size() - shown));
            }
        } else {
            String description = describe(result);
            if (!description.isBlank()) answer.append('\n').append(description);
        }
        return answer.substring(0, Math.min(MAX_LENGTH, answer.length()));
    }

    public static String todo(JsonNode result) {
        if (result == null || !result.isObject() || !result.path("items").isArray()
                || !result.path("total").canConvertToLong()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        long total = result.path("total").asLong();
        if (total == 0) return MessageUtils.resolve("agent.todo.empty");
        StringBuilder answer = new StringBuilder(MessageUtils.resolve("agent.todo.count", total));
        int shown = 0;
        for (JsonNode item : result.path("items")) {
            if (shown >= MAX_ITEMS) break;
            shown++;
            long id = item.path("id").asLong(-1);
            if (id < 1) throw new BusinessException(ErrorCode.REQUEST_INVALID);
            String applicant = plain(item.path("applicantName").asText(""));
            String code = plain(item.path("leaveType").asText(""));
            String key = "agent.todo.leave." + code;
            String leaveType = MessageUtils.resolve(key);
            if (key.equals(leaveType)) leaveType = code;
            String due = plain(item.path("dueAt").asText(""));
            answer.append('\n').append(MessageUtils.resolve("agent.todo.item", shown, id, applicant, leaveType));
            int halfDays = item.path("durationHalfDays").asInt(0);
            if (halfDays > 0) {
                answer.append(' ').append(MessageUtils.resolve("agent.todo.duration",
                        BigDecimal.valueOf(halfDays).divide(BigDecimal.valueOf(2)).stripTrailingZeros().toPlainString()));
            }
            if (!due.isBlank()) answer.append(' ').append(MessageUtils.resolve("agent.todo.due", due));
            if (item.path("overdue").asBoolean(false)) answer.append(' ').append(MessageUtils.resolve("agent.todo.overdue"));
        }
        if (total > shown) answer.append('\n').append(MessageUtils.resolve("agent.todo.more", total - shown));
        return answer.toString();
    }

    private static String plain(String value) {
        String clean = value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").strip();
        clean = clean.replaceAll("[\\\\`*_\\[\\]()<>]", "");
        return clean.substring(0, Math.min(100, clean.length()));
    }

    private static String describe(JsonNode node) {
        if (node == null || node.isNull()) return MessageUtils.resolve("agent.result.noDetails");
        if (node.isValueNode()) return plain(node.asText());
        List<String> fields = new ArrayList<>();
        collect(fields, "", node, 0);
        return fields.isEmpty() ? MessageUtils.resolve("agent.result.noDetails") : String.join(" · ", fields);
    }

    private static void collect(List<String> fields, String prefix, JsonNode node, int depth) {
        if (fields.size() >= MAX_FIELDS || depth > 2 || node == null || node.isNull()) return;
        if (node.isValueNode()) {
            fields.add((prefix.isBlank() ? "value" : plain(prefix)) + ": " + plain(node.asText()));
            return;
        }
        if (node.isArray()) {
            fields.add((prefix.isBlank() ? "items" : plain(prefix)) + ": " + node.size());
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext() && fields.size() < MAX_FIELDS) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if ("items".equals(entry.getKey())) continue;
            String path = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
            collect(fields, path, entry.getValue(), depth + 1);
        }
    }
}
