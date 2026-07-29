package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置。
 * 仅允许服务端环境变量配置，禁止回显或持久化到浏览器。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    /** MinIO 服务地址，例如 http://localhost:9000 */
    private String endpoint = "http://localhost:9000";

    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";

    /** 存储桶名称 */
    private String bucket = "ai-workmate";

    private String region;

    /** 启动时是否自动创建桶 */
    private boolean autoCreateBucket = true;
}
