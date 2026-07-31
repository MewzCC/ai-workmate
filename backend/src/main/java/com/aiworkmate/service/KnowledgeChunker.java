package com.aiworkmate.service;

import com.aiworkmate.config.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KnowledgeChunker {

    private final EmbeddingProperties properties;

    public List<String> split(String content) {
        String normalized = content == null ? "" : content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        if (normalized.isBlank()) return List.of();

        int maxChars = Math.max(200, properties.getChunkMaxChars());
        List<String> rawChunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n+")) {
            String value = paragraph.replaceAll("[\\t ]+", " ").strip();
            if (value.isBlank()) continue;
            if (value.length() > maxChars) {
                flush(current, rawChunks);
                splitLongParagraph(value, maxChars, rawChunks);
            } else if (current.isEmpty()) {
                current.append(value);
            } else if (current.length() + 2 + value.length() <= maxChars) {
                current.append("\n\n").append(value);
            } else {
                flush(current, rawChunks);
                current.append(value);
            }
        }
        flush(current, rawChunks);
        return addOverlap(rawChunks);
    }

    private void splitLongParagraph(String paragraph, int maxChars, List<String> chunks) {
        int offset = 0;
        while (offset < paragraph.length()) {
            int upper = Math.min(paragraph.length(), offset + maxChars);
            int end = upper;
            if (upper < paragraph.length()) {
                int minimum = offset + maxChars * 3 / 5;
                for (int index = upper - 1; index >= minimum; index--) {
                    char value = paragraph.charAt(index);
                    if ("。！？；.!?;\n ".indexOf(value) >= 0) {
                        end = index + 1;
                        break;
                    }
                }
            }
            chunks.add(paragraph.substring(offset, end).strip());
            offset = end;
            while (offset < paragraph.length() && Character.isWhitespace(paragraph.charAt(offset))) {
                offset++;
            }
        }
    }

    private List<String> addOverlap(List<String> rawChunks) {
        if (rawChunks.size() < 2) return List.copyOf(rawChunks);
        int overlap = Math.max(0, properties.getChunkOverlapChars());
        if (overlap == 0) return List.copyOf(rawChunks);
        List<String> result = new ArrayList<>(rawChunks.size());
        result.add(rawChunks.get(0));
        for (int index = 1; index < rawChunks.size(); index++) {
            String previous = rawChunks.get(index - 1);
            String prefix = previous.substring(Math.max(0, previous.length() - overlap)).strip();
            result.add(prefix.isBlank() ? rawChunks.get(index) : prefix + "\n\n" + rawChunks.get(index));
        }
        return result;
    }

    private void flush(StringBuilder current, List<String> chunks) {
        if (!current.isEmpty()) {
            chunks.add(current.toString().strip());
            current.setLength(0);
        }
    }
}
