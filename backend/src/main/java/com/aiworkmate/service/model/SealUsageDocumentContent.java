package com.aiworkmate.service.model;

import org.springframework.core.io.Resource;

public record SealUsageDocumentContent(Resource resource, String mimeType, String filename) {
}
