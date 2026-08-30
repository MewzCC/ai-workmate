package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    private long imageMaxBytes = 10 * 1024 * 1024;
    private long fileMaxBytes = 20 * 1024 * 1024;
    private int extractedTextMaxChars = 120_000;

    /** 附件在对象存储中的 key 前缀 */
    private String storagePrefix = "chat-attachments/";

    /** 员工合同与档案附件的独立对象前缀 */
    private String employeeDocumentStoragePrefix = "employee-documents/";

    /** 用印留档文件的独立对象前缀 */
    private String sealDocumentStoragePrefix = "seal-documents/";

    /** 附件最长保留天数；小于等于 0 表示不启用自动清理 */
    private int attachmentMaxAgeDays = 30;
}
