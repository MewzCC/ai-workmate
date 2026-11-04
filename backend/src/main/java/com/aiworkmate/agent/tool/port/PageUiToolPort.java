package com.aiworkmate.agent.tool.port;

import java.math.BigDecimal;
import java.util.List;

/** Prepares a bounded browser state change only; cannot submit a form or invoke a business write. */
public interface PageUiToolPort {
    Prepared prepare(ToolActorContext context, Command command);
    record Field(String name, Object value) {
        public Field {
            if (!(value instanceof String || value instanceof BigDecimal || value instanceof Boolean))
                throw new IllegalArgumentException("UI fields must contain a scalar value");
        }
    }
    record Command(String pageId, String code, String targetPageId, Object recordId, List<Field> fields) {
        public Command {
            if (recordId != null && !validRecordId(recordId)) throw new IllegalArgumentException("Invalid UI record identifier");
            fields = List.copyOf(fields);
        }
        public static boolean validRecordId(Object value) {
            return value instanceof Long numericId && numericId > 0
                    || value instanceof String taskId && taskId.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        }
    }
    record FormContext(String sessionId, Long recordId, Integer version) { }
    record Prepared(String pageId, String code, String targetPageId, Object recordId, List<Field> fields, FormContext formContext) {
        public Prepared {
            if (recordId != null && !Command.validRecordId(recordId)) throw new IllegalArgumentException("Invalid UI record identifier");
            fields = List.copyOf(fields);
        }
    }
}
