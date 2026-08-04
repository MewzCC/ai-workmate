package com.aiworkmate.common;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Locale;

/**
 * 消息解析工具类。
 *
 * <p>非 Spring 管理的代码（如 {@link BusinessException} 静态构造、{@link ErrorCode} 枚举）
 * 通过此工具类解析 i18n 文案。Spring 容器启动后通过 {@link #setInstance} 注入 MessageSource。
 *
 * <p>解析失败时返回 key 本身（与 {@code useCodeAsDefaultMessage=true} 行为一致），
 * 避免影响主流程。
 */
@Component
public class MessageUtils {

    private static MessageUtils instance;

    private final MessageSource messageSource;

    public MessageUtils(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @PostConstruct
    public void init() {
        instance = this;
    }

    /**
     * 按当前请求 locale 解析消息。无请求上下文时按系统默认 locale。
     */
    public static String resolve(String key, Object... args) {
        if (instance == null) {
            // 容器尚未初始化（如单元测试），直接返回 key
            return key;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return instance.messageSource.getMessage(key, args, key, locale);
    }

    /**
     * 按指定 locale 解析消息。
     */
    public static String resolve(String key, Locale locale, Object... args) {
        if (instance == null) {
            return key;
        }
        return instance.messageSource.getMessage(key, args, key, locale);
    }

    static void setInstance(MessageUtils utils) {
        instance = utils;
    }
}
