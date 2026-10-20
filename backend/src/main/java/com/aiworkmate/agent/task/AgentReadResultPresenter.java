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
        if ("dashboard.overview.query".equals(toolCode)) return dashboard(result);
        if ("observability.overview.query".equals(toolCode)) return observability(result);
        if ("approval.application.mine.query".equals(toolCode)) return myApplications(result);
        if ("approval.application.detail.query".equals(toolCode)) return applicationDetail(result);
        if ("meeting.room.query".equals(toolCode)) return meetingRooms(result);
        if ("meeting.room.create".equals(toolCode) || "meeting.room.update".equals(toolCode)) return meetingRoom(result, true);
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

    private static String meetingRooms(JsonNode result) {
        if (result == null || !result.path("items").isArray() || !result.path("total").canConvertToLong()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        StringBuilder answer = new StringBuilder(MessageUtils.resolve("agent.meeting.rooms", result.path("total").asLong()));
        int shown = 0;
        for (JsonNode room : result.path("items")) {
            if (shown >= MAX_ITEMS || answer.length() > 750) break;
            answer.append('\n').append(meetingRoom(room, false));
            shown++;
        }
        if (result.path("total").asLong() > shown) answer.append('\n').append(MessageUtils.resolve("agent.result.more", result.path("total").asLong() - shown));
        return answer.substring(0, Math.min(MAX_LENGTH, answer.length()));
    }

    private static String meetingRoom(JsonNode room, boolean saved) {
        if (room == null || room.path("roomId").asLong() < 1 || !room.path("version").canConvertToInt()
                || !List.of("OPEN", "CLOSED").contains(room.path("status").asText())) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        String status = MessageUtils.resolve("agent.meeting.status." + room.path("status").asText());
        return saved ? MessageUtils.resolve("agent.meeting.saved", room.path("roomId").asLong(),
                plain(room.path("name").asText()), plain(room.path("code").asText()), status, room.path("version").asInt())
                : MessageUtils.resolve("agent.meeting.room", room.path("roomId").asLong(), plain(room.path("name").asText()),
                plain(room.path("code").asText()), room.path("capacity").asInt(), status, room.path("version").asInt());
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

    private static String applicationDetail(JsonNode result) {
        if (result == null || !result.path("application").isObject() || !result.path("fields").isArray()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        JsonNode application = result.path("application");
        StringBuilder answer = new StringBuilder(MessageUtils.resolve("agent.application.item",
                application.path("applicationId").asLong(), plain(application.path("formName").asText()),
                MessageUtils.resolve("agent.application.status." + application.path("status").asText()),
                application.path("version").asInt()));
        answer.append('\n').append(MessageUtils.resolve("agent.application.schema." + result.path("schemaSource").asText()));
        int shown = 0;
        for (JsonNode field : result.path("fields")) {
            if (shown >= MAX_ITEMS || answer.length() > 750) break;
            shown++;
            List<String> values = new ArrayList<>();
            field.path("values").forEach(value -> values.add(plain(value.asText())));
            answer.append('\n').append(plain(field.path("label").asText())).append(": ")
                    .append(field.path("redacted").asBoolean() ? MessageUtils.resolve("agent.application.redacted")
                            : values.isEmpty() ? MessageUtils.resolve("agent.application.unfilled") : String.join(" · ", values));
        }
        if (result.path("fields").size() > shown || answer.length() > MAX_LENGTH) {
            answer.setLength(Math.min(answer.length(), 900));
            answer.append('\n').append(MessageUtils.resolve("agent.application.detailMore"));
        }
        return answer.substring(0, Math.min(MAX_LENGTH, answer.length()));
    }

    private static String myApplications(JsonNode result) {
        if (result == null || !result.path("items").isArray() || !result.path("total").canConvertToLong()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        StringBuilder answer = new StringBuilder(MessageUtils.resolve("agent.application.count", result.path("total").asLong()));
        int shown = 0;
        for (JsonNode item : result.path("items")) {
            if (shown >= MAX_ITEMS || answer.length() >= MAX_LENGTH) break;
            shown++;
            String status = item.path("status").asText();
            if (!List.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED").contains(status)) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            answer.append('\n').append(MessageUtils.resolve("agent.application.item", item.path("applicationId").asLong(),
                    plain(item.path("formName").asText()), MessageUtils.resolve("agent.application.status." + status),
                    item.path("version").asInt()));
        }
        if (result.path("total").asLong() > shown) answer.append('\n').append(MessageUtils.resolve("agent.result.more", result.path("total").asLong() - shown));
        return answer.substring(0, Math.min(MAX_LENGTH, answer.length()));
    }

    private static String observability(JsonNode result) {
        if (result == null || !result.isObject() || !result.path("stats").isObject()
                || !result.path("stats").path("total").canConvertToLong()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        var stats = result.path("stats");
        long total = stats.path("total").asLong();
        StringBuilder answer = new StringBuilder(MessageUtils.resolve("agent.observability.window",
                plain(result.path("from").asText()), plain(result.path("to").asText())));
        if (total == 0) return answer.append('\n').append(MessageUtils.resolve("agent.observability.empty")).toString();
        long failed = stats.path("failed").asLong();
        String rate = BigDecimal.valueOf(failed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP).toPlainString();
        answer.append('\n').append(MessageUtils.resolve("agent.observability.counts", total,
                stats.path("succeeded").asLong(), failed, stats.path("blocked").asLong(), rate));
        if (result.path("p95DurationMs").canConvertToLong() && result.path("p95DurationMs").asLong() > 0) {
            answer.append('\n').append(MessageUtils.resolve("agent.observability.latency",
                    result.path("p95DurationMs").asLong()));
        }
        if (result.path("categoriesTruncated").asBoolean()) {
            answer.append('\n').append(MessageUtils.resolve("agent.observability.truncated"));
        }
        return answer.substring(0, Math.min(MAX_LENGTH, answer.length()));
    }

    private static String dashboard(JsonNode result) {
        if (result == null || !result.isObject() || !result.path("metrics").isArray()
                || !result.path("trends").isArray() || !result.path("days").canConvertToInt()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        StringBuilder answer = new StringBuilder(MessageUtils.resolve(
                "agent.dashboard.overview", result.path("days").asInt()));
        for (JsonNode metric : result.path("metrics")) {
            String code = metric.path("code").asText();
            if (!List.of("PENDING_TODOS", "OVERDUE_TODOS", "MY_APPLICATIONS", "UNREAD_MESSAGES").contains(code)
                    || !metric.path("value").canConvertToLong()) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            answer.append('\n').append(MessageUtils.resolve("agent.dashboard.metric." + code,
                    metric.path("value").asLong()));
        }
        long submitted = 0;
        long completed = 0;
        for (JsonNode trend : result.path("trends")) {
            submitted += trend.path("submitted").asLong();
            completed += trend.path("completed").asLong();
        }
        answer.append('\n').append(MessageUtils.resolve("agent.dashboard.trend", submitted, completed));
        if (result.path("distributionTruncated").asBoolean()) {
            answer.append('\n').append(MessageUtils.resolve("agent.dashboard.distributionTruncated"));
        }
        return answer.substring(0, Math.min(MAX_LENGTH, answer.length()));
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
