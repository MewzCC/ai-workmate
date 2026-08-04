package com.aiworkmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Locale 解析配置。
 *
 * <p>仅按 {@code Accept-Language} 请求头解析，不使用 Cookie 或 Session。
 * 支持的语言固定为 {@code zh-CN} 与 {@code en-US}，默认 {@code zh-CN}。
 * 前端必须携带与当前 i18next 语言一致的 {@code Accept-Language}。
 */
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.SIMPLIFIED_CHINESE, Locale.US));
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }
}
