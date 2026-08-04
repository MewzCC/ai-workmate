package com.aiworkmate.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常。
 *
 * <p>构造时不立即解析 i18n 文案，只在 {@link #getMessage()} 时按当前请求 locale 解析，
 * 保证异常抛出线程与响应线程 locale 一致。
 *
 * <p>使用方式：
 * <ul>
 *   <li>{@code new BusinessException(ErrorCode.XXX)} — 使用 ErrorCode 的 messageKey 解析</li>
 *   <li>{@code new BusinessException(ErrorCode.XXX, "some.message.key")} — 使用指定 messageKey</li>
 *   <li>{@code new BusinessException(HttpStatus.BAD_REQUEST, "已解析文案")} — 直接传入已解析文案（向后兼容）</li>
 * </ul>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String errorCode;
    private final HttpStatus status;
    private final String messageKey;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.code = status.value();
        this.errorCode = null;
        this.messageKey = null;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessageKey());
    }

    /**
     * @param messageKey 当以 {@code error.XXX} / {@code validation.XXX} 开头时按 i18n key 解析；
     *                   否则视为已解析文案直接展示（向后兼容历史调用）。
     */
    public BusinessException(ErrorCode errorCode, String messageKey) {
        super(messageKey);
        this.status = errorCode.getStatus();
        this.code = errorCode.getCode();
        this.errorCode = errorCode.getErrorCode();
        this.messageKey = messageKey;
    }

    @Override
    public String getMessage() {
        if (messageKey == null) {
            return super.getMessage();
        }
        // 仅对符合 i18n key 命名规则的 messageKey 走 MessageSource 解析
        if (looksLikeMessageKey(messageKey)) {
            return MessageUtils.resolve(messageKey);
        }
        return messageKey;
    }

    private static boolean looksLikeMessageKey(String value) {
        // i18n key 约定：以 error. / validation. / 业务命名空间. 开头，且不含空格
        return (value.startsWith("error.") || value.startsWith("validation.") || value.startsWith("oa.")
                || value.startsWith("chat.") || value.startsWith("knowledge.") || value.startsWith("access."))
                && !value.contains(" ");
    }
}
