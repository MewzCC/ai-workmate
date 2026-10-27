package com.aiworkmate.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Test-only loopback client and independent least-privilege Cookie login fixture. */
final class AgentHttpTestFixture {
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PasswordEncoder passwords;
    private final int port;
    private final String language;

    AgentHttpTestFixture(JdbcTemplate jdbc, ObjectMapper json, PasswordEncoder passwords, int port, String language) {
        this.jdbc = jdbc;
        this.json = json;
        this.passwords = passwords;
        this.port = port;
        this.language = language;
    }

    static HttpClient client() { return HTTP; }

    Actor createAndLogin(long tenant, List<String> permissions) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String role = "HTTP_IT_" + suffix.substring(0, 24);
        String email = "http-it-" + suffix + "@example.invalid";
        String password = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO rbac_role(code,name,description,tenant_id,builtin) VALUES (?,'HTTP reader','isolated test',?,false)", role, tenant);
        long id = jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id,role) VALUES (?,?,?,?,?) RETURNING id", Long.class,
                email, passwords.encode(password), email, tenant, role);
        for (String permission : permissions) {
            jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,?)", tenant, role, permission);
        }
        String payload = json.createObjectNode().put("email", email).put("password", password).put("remember", false).toString();
        var response = HTTP.send(request("POST", "/api/auth/login/password", null, null, payload).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        String cookie = setCookie.split(";", 2)[0];
        assertThat(setCookie.substring(cookie.length())).contains("HttpOnly", "SameSite=Strict");
        assertThat(json.readTree(response.body()).path("data").has("token")).isFalse();
        return new Actor(id, role, cookie);
    }

    HttpRequest.Builder request(String method, String path, Actor actor, String key, String body) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Accept-Language", language);
        if (actor != null) builder.header("Cookie", actor.cookie());
        if (key != null) builder.header("Idempotency-Key", key);
        if (body != null) builder.header("Content-Type", "application/json");
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    }

    record Actor(long id, String role, String cookie) {
        @Override public String toString() { return "HTTP test actor " + id; }
    }
}
