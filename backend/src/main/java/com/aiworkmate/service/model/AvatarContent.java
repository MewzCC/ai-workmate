package com.aiworkmate.service.model;

import org.springframework.core.io.Resource;

public record AvatarContent(Resource resource, String mimeType) {
}
