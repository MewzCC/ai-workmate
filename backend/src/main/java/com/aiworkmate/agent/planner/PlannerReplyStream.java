package com.aiworkmate.agent.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Request-scoped presentation only: never exposes tool arguments or authorizes execution. */
public final class PlannerReplyStream {
    private static final ThreadLocal<Consumer<String>> LISTENER = new ThreadLocal<>();
    private static final Pattern SUMMARY = Pattern.compile("^\\s*\\{\\s*\"summary\"\\s*:\\s*\"");
    private PlannerReplyStream() { }

    public static Consumer<String> listener() { return LISTENER.get(); }

    public static <T> T observe(Consumer<String> listener, Supplier<T> operation) {
        Consumer<String> previous = LISTENER.get();
        LISTENER.set(listener);
        try { return operation.get(); }
        finally { if (previous == null) LISTENER.remove(); else LISTENER.set(previous); }
    }

    public static Consumer<String> summaryChunks(ObjectMapper mapper, Consumer<String> listener) {
        StringBuilder raw = new StringBuilder();
        String[] last = {""};
        return chunk -> {
            if (chunk == null) return;
            if (raw.length() + chunk.length() > 32768) throw new IllegalArgumentException("Plan stream too large");
            raw.append(chunk);
            var match = SUMMARY.matcher(raw);
            if (!match.find()) return;
            int start = match.end(), end = start;
            boolean escaped = false;
            for (; end < raw.length(); end++) {
                char c = raw.charAt(end);
                if (!escaped && c == '"') break;
                if (!escaped && c == '\\') escaped = true;
                else escaped = false;
            }
            try {
                String text = mapper.readValue("\"" + raw.substring(start, end) + "\"", String.class);
                if (text.length() > 500) return;
                if (!text.equals(last[0])) { last[0] = text; listener.accept(text); }
            } catch (com.fasterxml.jackson.core.JsonProcessingException incompleteEscape) {
                // Wait for the rest of an escaped character; never emit malformed JSON or raw arguments.
            }
        };
    }
}
