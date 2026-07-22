package com.aiworkmate.service.model;

import org.springframework.core.io.Resource;

public record AttachmentContent(Resource resource, String mimeType, String filename) {
}
