package com.aiworkmate.service;

import com.aiworkmate.service.model.ParsedFile;

import java.nio.file.Path;

public interface FileParserService {

    ParsedFile parse(Path path, String filename);
}
