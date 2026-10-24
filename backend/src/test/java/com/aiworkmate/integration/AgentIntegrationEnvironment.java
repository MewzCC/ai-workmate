package com.aiworkmate.integration;

import org.flywaydb.core.Flyway;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Test-only owner of one disposable database/schema and one native Redis child process. */
public final class AgentIntegrationEnvironment implements AutoCloseable {
    private final String schema = "agent_it_" + UUID.randomUUID().toString().replace("-", "");
    private String controlUrl, url, username, password;
    private Process redis;
    private int redisPort;
    private Thread shutdownHook;
    private boolean databaseOwned, prepared, closed;

    public String schema() { return schema; }

    public void verifyBindings(JdbcTemplate jdbc, StringRedisTemplate template) {
        if (!schema.equals(jdbc.queryForObject("SELECT current_database()", String.class))
                || !schema.equals(jdbc.queryForObject("SELECT current_schema()", String.class))) {
            throw new IllegalStateException("Integration datasource is not bound to its owned schema");
        }
        if (!prepared || redis == null || !redis.isAlive()) {
            throw new IllegalStateException("Integration Redis process is not ready");
        }
        Properties info = template.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info("server"));
        if (info == null || !Long.toString(redis.pid()).equals(info.getProperty("process_id"))) {
            throw new IllegalStateException("Integration Redis binding does not match its owned process");
        }
    }

    public synchronized void register(DynamicPropertyRegistry registry) {
        if (closed) throw new IllegalStateException("Integration environment is closed");
        if (!prepared) prepare();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + schema);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.data.redis.url", () -> "redis://127.0.0.1:" + redisPort + "/0");
        registry.add("spring.data.redis.password", () -> "");
        registry.add("agent.enabled", () -> "false");
        registry.add("agent.planning-enabled", () -> "false");
        registry.add("agent.execution-enabled", () -> "false");
        registry.add("agent.write-tools-enabled", () -> "false");
        registry.add("agent.retention-cleanup-enabled", () -> "false");
        registry.add("agent.retention-cleanup-cron", () -> "-");
        registry.add("agent.confirmation.expiry-delay-ms", () -> "3600000");
        registry.add("app.hr.employee-change-apply-cron", () -> "-");
        registry.add("app.upload.attachment-cleanup-cron", () -> "-");
        registry.add("logging.level.com.aiworkmate", () -> "INFO");
        registry.add("logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration", () -> "ERROR");
    }

    private void prepare() {
        controlUrl = requireLocalDatabaseUrl(System.getenv("AGENT_TEST_DB_URL"));
        url = controlUrl.substring(0, controlUrl.lastIndexOf('/') + 1) + schema;
        username = System.getenv().getOrDefault("AGENT_TEST_DB_USERNAME", "postgres");
        password = System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD", "postgres");
        String executable = System.getenv("AGENT_TEST_REDIS_BINARY");
        if (executable == null || !Path.of(executable).isAbsolute() || !Files.isRegularFile(Path.of(executable))
                || !Files.isExecutable(Path.of(executable))) {
            throw new IllegalArgumentException("AGENT_TEST_REDIS_BINARY must name an existing absolute Redis executable");
        }
        shutdownHook = new Thread(this::close, "cleanup-" + schema);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            try (var connection = DriverManager.getConnection(controlUrl, username, password);
                 var query = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname=?")) {
                query.setString(1, schema);
                try (var rows = query.executeQuery()) {
                    if (rows.next()) throw new IllegalStateException("Refusing to reuse an existing database");
                }
                // template0 excludes application objects that could be inherited from template1.
                try (var create = connection.createStatement()) {
                    create.execute("CREATE DATABASE " + schema + " TEMPLATE template0");
                }
                databaseOwned = true;
            }
            // Legacy migrations can refer to public or unqualified objects. Run only in our new database.
            Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").load().migrate();
            Path directory = Path.of("target", "agent-it", schema).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            try (var reservation = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
                redisPort = reservation.getLocalPort();
            }
            redis = new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", Integer.toString(redisPort),
                    "--save", "", "--appendonly", "no", "--dir", directory.toString())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
            awaitRedis();
            prepared = true;
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw new IllegalStateException("Could not prepare isolated Agent integration resources", failure);
        }
    }

    private void awaitRedis() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && redis.isAlive()) {
            try (var socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", redisPort), 500);
                socket.setSoTimeout(500);
                socket.getOutputStream().write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII));
                var input = socket.getInputStream();
                byte[] reply = input.readNBytes(7);
                if ("+PONG\r\n".equals(new String(reply, StandardCharsets.US_ASCII))) {
                    socket.getOutputStream().write("*2\r\n$4\r\nINFO\r\n$6\r\nserver\r\n".getBytes(StandardCharsets.US_ASCII));
                    if (belongsToRedisProcess(readInfo(input), redis.pid())) return;
                }
            } catch (java.io.IOException notReady) { /* bounded readiness probe, never another server */ }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Owned Redis process did not become ready; inspect its target/agent-it log");
    }

    private String readInfo(java.io.InputStream input) throws java.io.IOException {
        var header = new StringBuilder();
        for (int next; (next = input.read()) != -1 && next != '\n'; ) {
            if (header.length() >= 32) throw new java.io.IOException("Unbounded Redis bulk header");
            header.append((char) next);
        }
        try {
            if (header.isEmpty() || header.charAt(0) != '$') throw new NumberFormatException();
            int length = Integer.parseInt(header.substring(1).trim());
            if (length < 1 || length > 16384) throw new NumberFormatException();
            byte[] body = input.readNBytes(length);
            if (body.length != length) throw new java.io.IOException("Incomplete Redis server information");
            return new String(body, StandardCharsets.US_ASCII);
        } catch (NumberFormatException invalid) { throw new java.io.IOException("Invalid Redis bulk header", invalid); }
    }

    static boolean belongsToRedisProcess(String info, long pid) {
        return info.lines().filter(line -> line.startsWith("process_id:"))
                .toList().equals(java.util.List.of("process_id:" + pid));
    }

    static String requireLocalDatabaseUrl(String value) {
        try {
            if (value == null || !value.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI uri = URI.create(value.substring(5));
            if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPath() == null || !uri.getPath().matches("/[a-zA-Z0-9_-]+")) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Agent integration tests require a plain localhost PostgreSQL URL");
        }
    }

    static boolean isOwnedSchemaName(String value) {
        return value != null && value.matches("agent_it_[a-f0-9]{32}");
    }

    @Override public synchronized void close() {
        if (closed) return;
        try {
            if (redis != null && redis.isAlive()) {
                redis.destroy();
                if (!redis.waitFor(3, TimeUnit.SECONDS)) {
                    redis.destroyForcibly();
                    if (!redis.waitFor(3, TimeUnit.SECONDS)) throw new IllegalStateException("Owned Redis did not exit");
                }
            }
        } catch (InterruptedException interrupted) {
            if (redis != null) redis.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing owned Redis", interrupted);
        } finally {
            try {
                if (databaseOwned) {
                    if (!isOwnedSchemaName(schema)) throw new IllegalStateException("Refusing unsafe schema cleanup");
                    try (var connection = DriverManager.getConnection(controlUrl, username, password);
                         var drop = connection.createStatement()) {
                        // Never force-disconnect sessions; a live pool must make cleanup fail visibly.
                        drop.execute("DROP DATABASE IF EXISTS " + schema);
                    } catch (java.sql.SQLException failure) {
                        throw new IllegalStateException("Could not drop the owned integration database", failure);
                    }
                    databaseOwned = false;
                }
            } finally {
                // Keep the hook and allow a later close() retry when resource cleanup failed.
                closed = !databaseOwned && (redis == null || !redis.isAlive());
                if (closed && shutdownHook != null) {
                    try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                    catch (IllegalStateException shuttingDown) { /* JVM is already running the owner cleanup hook */ }
                }
            }
        }
    }
}
