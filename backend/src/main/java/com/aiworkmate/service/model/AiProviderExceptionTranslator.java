package com.aiworkmate.service.model;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeoutException;

public final class AiProviderExceptionTranslator {

    private AiProviderExceptionTranslator() {
    }

    public static BusinessException translate(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return businessException;
        }
        if (error instanceof WebClientResponseException responseException) {
            return switch (responseException.getStatusCode().value()) {
                case 401, 403 -> unavailable("DeepSeek API Key 无效或已失效，请检查服务端配置");
                case 429 -> unavailable("DeepSeek 请求过于频繁，请稍后重试");
                default -> responseException.getStatusCode().is5xxServerError()
                        ? unavailable("DeepSeek 服务暂时不可用，请稍后重试")
                        : unavailable("DeepSeek 请求失败，请检查模型与接口配置");
            };
        }
        if (error instanceof TimeoutException) {
            return unavailable("DeepSeek 响应超时，请稍后重试");
        }
        return unavailable("AI 对话服务暂时不可用");
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.AI_CHAT_UNAVAILABLE, message);
    }
}
