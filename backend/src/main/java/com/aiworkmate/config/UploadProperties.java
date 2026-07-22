package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    private String directory = "./data/chat-attachments";
    private long imageMaxBytes = 10 * 1024 * 1024;
    private long fileMaxBytes = 20 * 1024 * 1024;
    private int extractedTextMaxChars = 120_000;
}
