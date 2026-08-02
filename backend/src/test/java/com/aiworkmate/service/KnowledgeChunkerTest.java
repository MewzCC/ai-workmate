package com.aiworkmate.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    @Test
    void shouldPreferParagraphBoundariesAndAddOverlap() {
        KnowledgeChunker chunker = new KnowledgeChunker();

        String first = "A".repeat(150);
        String second = "B".repeat(100);
        List<String> chunks = chunker.split(first + "\n\n" + second, 200, 20);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).isEqualTo(first);
        assertThat(chunks.get(1)).startsWith("A".repeat(20) + "\n\n").endsWith(second);
    }

    @Test
    void shouldReturnNoChunksForBlankContent() {
        assertThat(new KnowledgeChunker().split(" \n "))
                .isEmpty();
    }

    @Test
    void shouldUsePerKnowledgeBaseSettings() {
        KnowledgeChunker chunker = new KnowledgeChunker();

        String first = "A".repeat(300);
        String second = "B".repeat(100);
        List<String> chunks = chunker.split(first + "\n\n" + second, 500, 0);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains(first).contains(second);
    }
}
