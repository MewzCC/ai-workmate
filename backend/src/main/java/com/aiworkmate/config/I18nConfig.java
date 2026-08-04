package com.aiworkmate.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * 国际化消息源配置。
 *
 * <p>加载 classpath:i18n/messages_*.properties，按请求 Accept-Language 解析对应语言的文案。
 * basename 固定为 {@code i18n.messages}，禁止在代码中硬编码中文文案。
 *
 * <p>同时把 Bean Validation 的消息解析也指向同一 MessageSource，
 * 让 {@code @Valid} 注解的 {@code message = "{validation.xxx}"} 占位符走 i18n 资源文件。
 */
@Configuration
public class I18nConfig {

    /**
     * Spring Boot 默认 {@code spring.messages.basename} 也能配置，但本项目显式声明 bean，
     * 避免被自动配置覆盖，并统一控制编码与回退行为。
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        // 找不到 key 时返回 key 本身，避免抛 NoSuchMessageException 影响主流程
        source.setUseCodeAsDefaultMessage(true);
        // 找不到对应语言时回退到默认 messages.properties
        source.setFallbackToSystemLocale(false);
        return source;
    }

    /**
     * 让 Jakarta Bean Validation 的 {@code {validation.xxx}} 占位符走上面的 MessageSource，
     * 而不是默认的 ValidationMessages.properties。
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        return bean;
    }
}

