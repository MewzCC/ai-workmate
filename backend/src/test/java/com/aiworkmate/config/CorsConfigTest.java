package com.aiworkmate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(CorsConfig.class, LocaleConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean("messageSource", StaticMessageSource.class, () -> {
                var source = new StaticMessageSource();
                source.addMessage("error.PERMISSION_DENIED", Locale.US, "Permission denied");
                source.addMessage("error.PERMISSION_DENIED", Locale.SIMPLIFIED_CHINESE, "权限不足");
                return source;
            });

    @Test void emptyConfigurationDeniesCrossOriginButPreservesRequestsWithoutOrigin() {
        context.withPropertyValues("app.cors.allowed-origins=").run(app -> {
            var filter = app.getBean(CorsFilter.class);
            assertThat(invoke(filter, "https://untrusted.example", "GET").getStatus()).isEqualTo(403);
            assertThat(invoke(filter, null, "GET").getStatus()).isEqualTo(204);
        });
    }

    @Test void configuredOriginsSupportCredentialedPreflightAndRequiredClientHeaders() {
        context.withPropertyValues("app.cors.allowed-origins=http://localhost:3000, http://127.0.0.1:3001,https://oa.example.invalid").run(app -> {
            for (String origin : new String[]{"http://localhost:3000", "http://127.0.0.1:3001", "https://oa.example.invalid"}) {
                var response = invoke(app.getBean(CorsFilter.class), origin, "OPTIONS");
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
                assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
                assertThat(response.getHeader("Access-Control-Allow-Headers")).contains("Idempotency-Key", "Last-Event-ID");
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://oa.example.invalid.evil.invalid", "https://sub.oa.example.invalid", "http://oa.example.invalid", "https://oa.example.invalid:444", "null"})
    void rejectsUntrustedOriginsBeforeBusinessCode(String origin) {
        context.withPropertyValues("app.cors.allowed-origins=https://oa.example.invalid").run(app -> {
            var response = invoke(app.getBean(CorsFilter.class), origin, "POST");
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
            assertThat(response.getContentType()).startsWith("application/json");
            var body = app.getBean(ObjectMapper.class).readTree(response.getContentAsString());
            assertThat(body.path("errorCode").asText()).isEqualTo("PERMISSION_DENIED");
            assertThat(body.path("message").asText()).isEqualTo("Permission denied");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.example.invalid", "null", "https://user:secret@example.invalid", "https://example.invalid/", "https://example.invalid/path", "https://example.invalid?x=1", "https://example.invalid#x", "ftp://example.invalid", "https://example.invalid:0", "https://example.invalid:65536", "https://example.invalid:", "https://example.invalid,,https://other.invalid"})
    void invalidServerConfigurationFailsStartupWithoutEchoingValues(String value) {
        context.withPropertyValues("app.cors.allowed-origins=" + value).run(app -> {
            assertThat(app).hasFailed();
            Throwable root = app.getStartupFailure();
            while (root.getCause() != null) root = root.getCause();
            assertThat(root).isInstanceOf(IllegalArgumentException.class);
            assertThat(root.getMessage()).isEqualTo("app.cors.allowed-origins must contain exact HTTP(S) origins");
        });
    }

    @Test void deniedResponseUsesRequestLanguage() {
        context.withPropertyValues("app.cors.allowed-origins=").run(app -> {
            var request = request("https://untrusted.example", "POST");
            request.addPreferredLocale(Locale.SIMPLIFIED_CHINESE);
            var response = new MockHttpServletResponse();
            app.getBean(CorsFilter.class).doFilter(request, response, (req, res) -> { throw new AssertionError("business reached"); });
            assertThat(response.getContentAsString()).contains("权限不足");
        });
    }

    @Test void deniedResponseUsesTheSharedSupportedLanguageNegotiationAndDefault() {
        context.withPropertyValues("app.cors.allowed-origins=").run(app -> {
            var request = request("https://untrusted.example", "POST");
            request.addPreferredLocale(Locale.FRANCE); // fr-FR first, then supported en-US.
            var response = new MockHttpServletResponse();
            app.getBean(CorsFilter.class).doFilter(request, response, (req, res) -> { throw new AssertionError("business reached"); });
            assertThat(response.getContentAsString()).contains("Permission denied");

            request = request("https://untrusted.example", "POST");
            request.setPreferredLocales(java.util.List.of(Locale.FRANCE));
            response = new MockHttpServletResponse();
            app.getBean(CorsFilter.class).doFilter(request, response, (req, res) -> { throw new AssertionError("business reached"); });
            assertThat(response.getContentAsString()).contains("权限不足");
        });
    }

    private static MockHttpServletResponse invoke(CorsFilter filter, String origin, String method) throws Exception {
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        filter.doFilter(request(origin, method), response, (req, res) -> { called.set(true); ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204); });
        if (origin != null && response.getStatus() == 403) assertThat(called).isFalse();
        return response;
    }

    private static MockHttpServletRequest request(String origin, String method) {
        var request = new MockHttpServletRequest(method, "/api/ai/tasks/plan");
        request.setServerName("api.example.invalid");
        request.addPreferredLocale(Locale.US);
        if (origin != null) request.addHeader("Origin", origin);
        if ("OPTIONS".equals(method)) {
            request.addHeader("Access-Control-Request-Method", "POST");
            request.addHeader("Access-Control-Request-Headers", "Content-Type,Accept-Language,X-Request-Id,Idempotency-Key,Last-Event-ID,Authorization");
        }
        return request;
    }
}
