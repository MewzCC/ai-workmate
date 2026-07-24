package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.UploadProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaFileParserServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseMarkdownWhenBrowserMimeTypeIsUnavailable() throws Exception {
        Path markdown = tempDir.resolve("release-notes.MD");
        Files.writeString(markdown, "# Release notes\n\n- Markdown upload works.");

        var parsed = parser().parse(markdown, markdown.getFileName().toString());

        assertThat(parsed.mimeType()).isEqualTo("text/markdown");
        assertThat(parsed.extractedText()).contains("# Release notes", "Markdown upload works");
        assertThat(parsed.image()).isFalse();
    }

    @Test
    void shouldRejectBinaryContentDisguisedAsMarkdown() throws Exception {
        Path markdown = tempDir.resolve("payload.md");
        Files.write(markdown, new byte[]{0, 1, 2, 3});

        assertThatThrownBy(() -> parser().parse(markdown, markdown.getFileName().toString()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容解析失败");
    }

    private TikaFileParserServiceImpl parser() {
        UploadProperties properties = new UploadProperties();
        properties.setExtractedTextMaxChars(120_000);
        return new TikaFileParserServiceImpl(properties);
    }
}
