package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.model.ParsedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final Tika tika = new Tika();

    @Override
    public ParsedFile parse(Path path, String filename) {
        try {
            String mimeType;
            try (InputStream input = Files.newInputStream(path)) {
                mimeType = tika.detect(input, filename);
            }
            if (IMAGE_TYPES.contains(mimeType)) return new ParsedFile(mimeType, null, true);
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
}
