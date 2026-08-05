package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.service.OcrService;
import com.aiworkmate.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TikaFileParserServiceImplTest {

    private static final Long USER_ID = 1001L;

    @TempDir
    Path tempDir;

    /** 单页空 PDF：无文本层（模拟扫描版 PDF 的 Tika 提取结果），Tika 可正常解析 */
    private static final byte[] BLANK_PDF = (
            "%PDF-1.4\n"
                    + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                    + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                    + "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n"
                    + "trailer\n<< /Root 1 0 R >>\n%%EOF"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    void shouldParseMarkdownWhenBrowserMimeTypeIsUnavailable() throws Exception {
        Path markdown = tempDir.resolve("release-notes.MD");
        Files.writeString(markdown, "# Release notes\n\n- Markdown upload works.");

        var parsed = parser().parse(markdown, markdown.getFileName().toString(), USER_ID);

        assertThat(parsed.mimeType()).isEqualTo("text/markdown");
        assertThat(parsed.extractedText()).contains("# Release notes", "Markdown upload works");
        assertThat(parsed.image()).isFalse();
    }

    @Test
    void shouldRejectBinaryContentDisguisedAsMarkdown() throws Exception {
        Path markdown = tempDir.resolve("payload.md");
        Files.write(markdown, new byte[]{0, 1, 2, 3});

        assertThatThrownBy(() -> parser().parse(markdown, markdown.getFileName().toString(), USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容解析失败");
    }

    @Test
    void shouldFallBackToOcrForScannedPdfWithoutTextLayer() throws Exception {
        Path pdf = tempDir.resolve("scanned.pdf");
        Files.write(pdf, BLANK_PDF);
        UploadProperties properties = new UploadProperties();
        properties.setExtractedTextMaxChars(120_000);
        OcrService ocrService = Mockito.mock(OcrService.class);
        when(ocrService.isAvailable()).thenReturn(true);
        when(ocrService.recognize(any(), any())).thenReturn("扫描版识别结果");
        TikaFileParserServiceImpl parser = new TikaFileParserServiceImpl(properties, ocrService, settingsService(false));

        var parsed = parser.parse(pdf, pdf.getFileName().toString(), USER_ID);

        assertThat(parsed.mimeType()).isEqualTo("application/pdf");
        assertThat(parsed.extractedText()).contains("扫描版识别结果");
        assertThat(parsed.image()).isFalse();
        verify(ocrService).recognize(any(), any());
    }

    @Test
    void shouldFailWhenScannedPdfOcrReturnsNoText() throws Exception {
        Path pdf = tempDir.resolve("blank-scanned.pdf");
        Files.write(pdf, BLANK_PDF);
        UploadProperties properties = new UploadProperties();
        properties.setExtractedTextMaxChars(120_000);
        OcrService ocrService = Mockito.mock(OcrService.class);
        when(ocrService.isAvailable()).thenReturn(true);
        when(ocrService.recognize(any(), any())).thenReturn(null);
        TikaFileParserServiceImpl parser = new TikaFileParserServiceImpl(properties, ocrService, settingsService(false));

        assertThatThrownBy(() -> parser.parse(pdf, pdf.getFileName().toString(), USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容解析失败");

        verify(ocrService).recognize(any(), any());
    }

    @Test
    void shouldUseOcrWhenForcePdfOcrEnabled() throws Exception {
        Path pdf = tempDir.resolve("force-ocr.pdf");
        Files.write(pdf, BLANK_PDF);
        UploadProperties properties = new UploadProperties();
        properties.setExtractedTextMaxChars(120_000);
        OcrService ocrService = Mockito.mock(OcrService.class);
        when(ocrService.isAvailable()).thenReturn(true);
        when(ocrService.recognize(any(), any())).thenReturn("强制 OCR 识别结果");
        TikaFileParserServiceImpl parser = new TikaFileParserServiceImpl(properties, ocrService, settingsService(true));

        var parsed = parser.parse(pdf, pdf.getFileName().toString(), USER_ID);

        assertThat(parsed.extractedText()).contains("强制 OCR 识别结果");
        verify(ocrService).recognize(any(), any());
    }

    @Test
    void shouldFailWhenForcePdfOcrEnabledAndOcrReturnsNoText() throws Exception {
        Path pdf = tempDir.resolve("force-ocr-blank.pdf");
        Files.write(pdf, BLANK_PDF);
        UploadProperties properties = new UploadProperties();
        properties.setExtractedTextMaxChars(120_000);
        OcrService ocrService = Mockito.mock(OcrService.class);
        when(ocrService.isAvailable()).thenReturn(true);
        when(ocrService.recognize(any(), any())).thenReturn(null);
        TikaFileParserServiceImpl parser = new TikaFileParserServiceImpl(properties, ocrService, settingsService(true));

        assertThatThrownBy(() -> parser.parse(pdf, pdf.getFileName().toString(), USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容解析失败");
    }

    private UserSettingsService settingsService(boolean forcePdfOcr) {
        UserSettingsService settingsService = Mockito.mock(UserSettingsService.class);
        when(settingsService.isForcePdfOcr(USER_ID)).thenReturn(forcePdfOcr);
        return settingsService;
    }

    private TikaFileParserServiceImpl parser() {
        UploadProperties properties = new UploadProperties();
        properties.setExtractedTextMaxChars(120_000);
        OcrService ocrService = Mockito.mock(OcrService.class);
        when(ocrService.isAvailable()).thenReturn(true);
        when(ocrService.recognize(any(), any())).thenReturn("识别文本");
        return new TikaFileParserServiceImpl(properties, ocrService, settingsService(false));
    }
}
