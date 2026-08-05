package com.aiworkmate.service;

import com.aiworkmate.service.model.ParsedFile;

import java.nio.file.Path;

public interface FileParserService {

    /**
     * 解析上传文件。
     *
     * @param userId 上传者，用于按用户偏好决定 PDF 是否强制 OCR
     */
    ParsedFile parse(Path path, String filename, Long userId);
}
