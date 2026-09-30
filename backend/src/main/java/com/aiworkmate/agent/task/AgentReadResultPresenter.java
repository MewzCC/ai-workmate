package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.MessageUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** Deterministic, bounded presentation of already-authorized tool output. Never feeds a planner. */
public final class AgentReadResultPresenter {
    private AgentReadResultPresenter() { }

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
            if (shown >= 20) break;
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
}
