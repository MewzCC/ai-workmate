package com.aiworkmate.service;

import com.aiworkmate.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    @Test
    void shouldPreferParagraphBoundariesAndAddOverlap() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setChunkMaxChars(200);
        properties.setChunkOverlapChars(20);
        KnowledgeChunker chunker = new KnowledgeChunker(properties);

        String first = "A".repeat(150);
        String second = "B".repeat(100);
        List<String> chunks = chunker.split(first + "\n\n" + second);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).isEqualTo(first);
        assertThat(chunks.get(1)).startsWith("A".repeat(20) + "\n\n").endsWith(second);
    }

    @Test
    void shouldReturnNoChunksForBlankContent() {
        assertThat(new KnowledgeChunker(new EmbeddingProperties()).split(" \n "))
                .isEmpty();
    }
}
