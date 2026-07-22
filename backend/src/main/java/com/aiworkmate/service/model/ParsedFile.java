package com.aiworkmate.service.model;

public record ParsedFile(String mimeType, String extractedText, boolean image) {
}
