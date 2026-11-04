package com.aiworkmate.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LocalizedMessagesExtension.class)
class BusinessExceptionLocalizationTest {
    @Test void resolvesParametersAtHandlingTimeWithoutChangingTheBusinessCode() {
        var error = new BusinessException(ErrorCode.REQUEST_INVALID,
                "validation.runtime.file.unsupportedType", "application/x-fixture");
        LocaleContextHolder.setLocale(Locale.US);
        assertThat(error.getMessage()).isEqualTo("Unsupported file type: application/x-fixture");
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        assertThat(error.getMessage()).isEqualTo("不支持该文件类型：application/x-fixture");
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_INVALID.getErrorCode());
    }

    @Test void freezesTheArgumentArrayAndDoesNotExposeItsMutableBackingArray() {
        Object[] arguments = {420L};
        var error = new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED, "validation.runtime.auth.lockedRetry", arguments);
        arguments[0] = 0L;
        error.getMessageArguments()[0] = 999L;
        LocaleContextHolder.setLocale(Locale.US);
        assertThat(error.getMessage()).contains("420 seconds").doesNotContain("999");
    }

    @Test void preservesTheActualFractionalUploadLimitInBothLanguages() {
        var error = new BusinessException(ErrorCode.REQUEST_INVALID, "validation.runtime.file.tooLarge", FileSizeLimit.describe(1572864L));
        assertThat(error.getMessage()).isEqualTo("文件不能超过 1.5MB");
        LocaleContextHolder.setLocale(Locale.US);
        assertThat(error.getMessage()).isEqualTo("The file must not exceed 1.5MB");
    }

    @Test void formatsSmallAndStandardLimitsWithoutRoundingThemToZeroMegabytes() {
        assertThat(FileSizeLimit.describe(4)).isEqualTo("4B");
        assertThat(FileSizeLimit.describe(1024)).isEqualTo("1KB");
        assertThat(FileSizeLimit.describe(5 * 1048576L)).isEqualTo("5MB");
    }

    @Test void httpErrorUsesTheRequestLocaleAndTheOriginalStatusAndCode() {
        LocaleContextHolder.setLocale(Locale.US);
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        var response = new GlobalExceptionHandler(source).handleBusiness(new BusinessException(
                ErrorCode.AUTH_ACCOUNT_LOCKED, "validation.runtime.auth.lockedRetry", 420L));
        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED.getStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED.getErrorCode());
        assertThat(response.getBody().getMessage()).contains("420 seconds");
    }

    @Test void preservesExistingLiteralConstructors() {
        assertThat(new BusinessException(HttpStatus.BAD_REQUEST, "Legacy literal").getMessage()).isEqualTo("Legacy literal");
        assertThat(new BusinessException(ErrorCode.REQUEST_INVALID, "Legacy literal").getMessage()).isEqualTo("Legacy literal");
    }

    @Test void allNewRuntimeMessagesExistDirectlyInEachLocaleWithoutFallback() throws Exception {
        var defaults = messages("messages.properties");
        var chinese = messages("messages_zh_CN.properties");
        var english = messages("messages_en_US.properties");
        var keys = defaults.stringPropertyNames().stream().filter(key -> key.startsWith("validation.runtime.")).toList();
        assertThat(keys).isNotEmpty();
        for (String key : keys) {
            assertThat(chinese.getProperty(key)).as("Chinese message %s", key).isNotBlank().isEqualTo(defaults.getProperty(key));
            assertThat(english.getProperty(key)).as("English message %s", key).isNotBlank().doesNotContainPattern("[一-龥]");
        }
    }

    @Test void serviceExceptionsDoNotReintroduceChineseMessageLiterals() throws Exception {
        var calls = java.util.regex.Pattern.compile("(?s)new BusinessException\\([^;]+?\\);");
        var chinese = java.util.regex.Pattern.compile("[\\p{IsHan}]");
        try (var sources = Files.walk(Path.of("src/main/java/com/aiworkmate/service"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = calls.matcher(Files.readString(source, StandardCharsets.UTF_8));
                while (matcher.find()) assertThat(chinese.matcher(matcher.group()).find())
                        .as("Business exception message must use a locale key: %s", source.getFileName()).isFalse();
            }
        }
    }

    private Properties messages(String resource) throws Exception {
        var result = new Properties();
        try (var stream = getClass().getResourceAsStream("/i18n/" + resource)) {
            assertThat(stream).isNotNull();
            result.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return result;
    }
}
