package com.aiworkmate.service;

import com.aiworkmate.service.model.ParsedFile;

import java.nio.file.Path;

public interface FileParserService {
    /** Validate a business upload independently of text extraction preferences and OCR availability. */
    default ParsedFile inspect(Path path, String filename, Long userId) {
        return parse(path, filename, userId);
    }

    /**
     * 解析上传文件。
     *
     * @param userId 上传者，用于按用户偏好决定 PDF 是否强制 OCR
     */
    ParsedFile parse(Path path, String filename, Long userId);
}
