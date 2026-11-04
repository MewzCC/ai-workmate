package com.aiworkmate.common;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

/** Makes message assertions independent of another test having started Spring first. */
public final class LocalizedMessagesExtension implements BeforeEachCallback, AfterEachCallback {
    private MessageUtils previousMessages;
    private LocaleContext previousLocale;

    @Override public void beforeEach(ExtensionContext context) {
        previousMessages = (MessageUtils) ReflectionTestUtils.getField(MessageUtils.class, "instance");
        previousLocale = LocaleContextHolder.getLocaleContext();
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        new MessageUtils(source).init();
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
    }

    @Override public void afterEach(ExtensionContext context) {
        MessageUtils.setInstance(previousMessages);
        LocaleContextHolder.setLocaleContext(previousLocale);
    }
}
