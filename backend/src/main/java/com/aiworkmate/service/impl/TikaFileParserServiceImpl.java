package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.OcrService;
import com.aiworkmate.service.UserSettingsService;
import com.aiworkmate.service.model.ParsedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikaFileParserServiceImpl implements FileParserService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "text/markdown", "text/csv", "application/csv"
    );

    private final UploadProperties properties;
    private final OcrService ocrService;
    private final UserSettingsService userSettingsService;
    private final Tika tika = new Tika();

    @Override
    public ParsedFile parse(Path path, String filename, Long userId) {
        try {
            if (isMarkdown(filename)) {
                return new ParsedFile("text/markdown", readMarkdown(path), false);
            }
            String mimeType;
            try (InputStream input = Files.newInputStream(path)) {
                mimeType = tika.detect(input, filename);
            }
            if (IMAGE_TYPES.contains(mimeType)) {
                String text = ocrService.recognize(path, filename);
                return new ParsedFile(mimeType, text, true);
            }
            if (isPdf(mimeType)) {
                String text = tika.parseToString(path).strip();
                boolean forceOcr = userSettingsService.isForcePdfOcr(userId);
                if (forceOcr || text.isBlank()) {
                    // 强制 OCR（用户设置）或扫描版 PDF 无文本层：交由 OCR 服务渲染页面逐页识别；
                    // 强制模式下 OCR 失败时回退文本层，避免上传因 OCR 抖动失败
                    String ocrText = ocrService.recognize(path, filename);
                    if (ocrText != null && !ocrText.isBlank()) {
                        return new ParsedFile(mimeType, limit(ocrText), false);
                    }
                    if (!text.isBlank()) {
                        return new ParsedFile(mimeType, limit(text), false);
                    }
                    throw new IOException("No extractable text");
                }
                return new ParsedFile(mimeType, limit(text), false);
            }
            if (!DOCUMENT_TYPES.contains(mimeType)) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID, "不支持该文件类型：" + mimeType);
            }
            String text = tika.parseToString(path).strip();
            return new ParsedFile(mimeType, limit(text), false);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Attachment parsing failed, filename={}", filename, ex);
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "文件内容解析失败，请检查文件是否损坏或加密");
        }
    }

    private String limit(String text) throws IOException {
        if (text.isBlank()) throw new IOException("No extractable text");
        int maxChars = properties.getExtractedTextMaxChars();
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private String readMarkdown(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.indexOf('\0') >= 0) {
            throw new IOException("Binary content is not a Markdown document");
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return limit(text.strip());
    }

    private boolean isPdf(String mimeType) {
        return "application/pdf".equals(mimeType);
    }

    private boolean isMarkdown(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".md") || normalized.endsWith(".markdown");
    }
}
