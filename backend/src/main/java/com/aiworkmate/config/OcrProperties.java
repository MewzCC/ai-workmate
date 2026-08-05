package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

    /** 是否启用 OCR；false 时视为引擎不可用 */
    private boolean enabled = true;
    /** PaddleOCR 微服务基地址 */
    private String baseUrl = "http://127.0.0.1:8686";
    /** 微服务访问密钥，未配置则不携带鉴权头 */
    private String apiKey = "";
    /** 单次识别超时（毫秒），CPU 单张约 1~3 秒，留裕量 */
    private int timeoutMs = 30000;
    /** 低于该置信度的识别块丢弃（微服务侧执行） */
    private double minConfidence = 0.6;
}
