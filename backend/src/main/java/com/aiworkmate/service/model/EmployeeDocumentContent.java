package com.aiworkmate.service.model;

import org.springframework.core.io.Resource;

public record EmployeeDocumentContent(Resource resource, String mimeType, String filename) {
}
