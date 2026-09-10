package com.aiworkmate.service.impl;

import com.aiworkmate.config.IntegrationUpstreamProperties;
import com.aiworkmate.dto.IntegrationOptionsResponse;
import com.aiworkmate.service.IntegrationSandboxClient;
import com.aiworkmate.service.model.SandboxCallResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DefaultIntegrationSandboxClient implements IntegrationSandboxClient {
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> FORBIDDEN_AUTH_HEADERS = Set.of("host", "content-length", "cookie", "set-cookie");
    private final IntegrationUpstreamProperties properties;

    @Override
    public List<IntegrationOptionsResponse.Option> options() {
        return properties.getUpstreams().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> new IntegrationOptionsResponse.Option(entry.getKey(),
                        StringUtils.hasText(entry.getValue().getDisplayName()) ? entry.getValue().getDisplayName() : entry.getKey(),
                        usable(entry.getValue()))).toList();
    }

    @Override public boolean isRegistered(String code) { return properties.getUpstreams().containsKey(code); }
    @Override public boolean isAvailable(String code) { return usable(properties.getUpstreams().get(code)); }

    @Override
    public SandboxCallResult execute(String upstreamCode, String method, String relativePath, String body) {
        long started = System.nanoTime();
        try {
            IntegrationUpstreamProperties.Upstream upstream = properties.getUpstreams().get(upstreamCode);
            if (!usable(upstream)) return failed(started, "UPSTREAM_UNAVAILABLE");
            String normalizedMethod = method.toUpperCase(Locale.ROOT);
            if (!METHODS.contains(normalizedMethod)) return failed(started, "METHOD_NOT_ALLOWED");
            if (!safeRelativePath(relativePath)) return failed(started, "TARGET_REJECTED");
            URI base = normalizedBase(upstream);
            URI target = base.resolve(relativePath.substring(1));
            if (!sameOrigin(base, target)) return failed(started, "TARGET_REJECTED");
            HttpRequest.Builder request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Accept", "application/json").header("Content-Type", "application/json");
            applyServerCredential(request, upstream);
            if (("GET".equals(normalizedMethod) || "DELETE".equals(normalizedMethod)) && !StringUtils.hasText(body)) {
                request.method(normalizedMethod, HttpRequest.BodyPublishers.noBody());
            } else {
                request.method(normalizedMethod, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body, StandardCharsets.UTF_8));
            }
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds()))).build();
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            int max = Math.max(1024, properties.getMaxResponseBytes());
            byte[] bytes;
            try (InputStream stream = response.body()) { bytes = stream.readNBytes(max + 1); }
            if (bytes.length > max) return new SandboxCallResult("FAILED", response.statusCode(), elapsed(started), null, "RESPONSE_TOO_LARGE");
            String preview = new String(bytes, StandardCharsets.UTF_8).replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "");
            String outcome = response.statusCode() >= 200 && response.statusCode() < 300 ? "SUCCESS" : "FAILED";
            return new SandboxCallResult(outcome, response.statusCode(), elapsed(started), preview,
                    "SUCCESS".equals(outcome) ? null : "UPSTREAM_HTTP_ERROR");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(started, "REQUEST_INTERRUPTED");
        } catch (Exception exception) {
            return failed(started, "UPSTREAM_UNAVAILABLE");
        }
    }

    private boolean usable(IntegrationUpstreamProperties.Upstream upstream) {
        if (upstream == null || !upstream.isEnabled() || !upstream.isSandbox() || !StringUtils.hasText(upstream.getBaseUrl())) return false;
        try { normalizedBase(upstream); return true; } catch (RuntimeException ignored) { return false; }
    }
    private URI normalizedBase(IntegrationUpstreamProperties.Upstream upstream) {
        URI uri = URI.create(upstream.getBaseUrl().trim());
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        if ((!secure && !(upstream.isAllowInsecureHttp() && "http".equalsIgnoreCase(uri.getScheme())))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("invalid upstream");
        }
        String path = uri.getPath();
        return URI.create(uri.getScheme() + "://" + authority(uri) + (path.endsWith("/") ? path : path + "/"));
    }
    private String authority(URI uri) { return uri.getPort() < 0 ? uri.getHost() : uri.getHost() + ":" + uri.getPort(); }
    private boolean sameOrigin(URI base, URI target) { return base.getScheme().equalsIgnoreCase(target.getScheme()) && base.getHost().equalsIgnoreCase(target.getHost()) && base.getPort() == target.getPort() && target.getUserInfo() == null; }
    private boolean safeRelativePath(String path) {
        if (!StringUtils.hasText(path)) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return path.startsWith("/") && !path.startsWith("//") && !path.contains("\\")
                && !path.contains("\r") && !path.contains("\n") && !lower.contains("://")
                && !lower.contains("..") && !lower.contains("%2e") && !lower.contains("@");
    }
    private void applyServerCredential(HttpRequest.Builder request, IntegrationUpstreamProperties.Upstream upstream) {
        String name = upstream.getAuthHeaderName();
        if (!StringUtils.hasText(name) || !StringUtils.hasText(upstream.getAuthHeaderValue())) return;
        if (!name.matches("^[A-Za-z][A-Za-z0-9-]{0,63}$") || FORBIDDEN_AUTH_HEADERS.contains(name.toLowerCase(Locale.ROOT))) return;
        request.header(name, upstream.getAuthHeaderValue());
    }
    private SandboxCallResult failed(long started, String code) { return new SandboxCallResult("FAILED", null, elapsed(started), null, code); }
    private long elapsed(long started) { return Math.max(0, (System.nanoTime() - started) / 1_000_000); }
}
