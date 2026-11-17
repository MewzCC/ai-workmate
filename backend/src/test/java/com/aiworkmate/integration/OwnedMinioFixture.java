package com.aiworkmate.integration;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** One native MinIO process, new data directory and synthetic credentials, owned by one test class. */
final class OwnedMinioFixture implements AutoCloseable {
    private final String bucket = "approval-it-" + UUID.randomUUID().toString().replace("-", "");
    private final String username = UUID.randomUUID().toString().replace("-", "");
    private final String password = UUID.randomUUID().toString() + UUID.randomUUID();
    private Process process;
    private String endpoint;
    private Thread shutdown;

    synchronized void register(DynamicPropertyRegistry registry) {
        if (process == null) start();
        registry.add("app.minio.endpoint", () -> endpoint);
        registry.add("app.minio.access-key", () -> username);
        registry.add("app.minio.secret-key", () -> password);
        registry.add("app.minio.bucket", () -> bucket);
        registry.add("app.minio.auto-create-bucket", () -> "true");
    }

    private void start() {
        String configured = System.getenv("AGENT_TEST_MINIO_BINARY");
        if (configured == null || !Path.of(configured).isAbsolute() || !Files.isExecutable(Path.of(configured)))
            throw new IllegalStateException("AGENT_TEST_MINIO_BINARY must identify the native test MinIO executable");
        try {
            int api = port(), console = port();
            while (api == console) console = port();
            Path directory = Path.of("target", "minio-it", UUID.randomUUID().toString()).toAbsolutePath();
            Files.createDirectories(directory.resolve("data"));
            var builder = new ProcessBuilder(configured, "server", directory.resolve("data").toString(),
                    "--address", "127.0.0.1:" + api, "--console-address", "127.0.0.1:" + console)
                    .redirectErrorStream(true).redirectOutput(directory.resolve("server.log").toFile());
            var inherited = System.getenv();
            builder.environment().clear();
            for (String name : List.of("Path", "PATH", "SystemRoot", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP",
                    "USERPROFILE", "LOCALAPPDATA", "APPDATA", "HOME")) {
                if (inherited.containsKey(name)) builder.environment().put(name, inherited.get(name));
            }
            builder.environment().put("MINIO_ROOT_USER", username);
            builder.environment().put("MINIO_ROOT_PASSWORD", password);
            builder.environment().put("MINIO_BROWSER", "off");
            builder.environment().put("MINIO_SITE_REGION", "us-east-1");
            process = builder.start();
            shutdown = new Thread(this::close, "owned-minio-cleanup");
            Runtime.getRuntime().addShutdownHook(shutdown);
            endpoint = "http://127.0.0.1:" + api;
            var client = MinioClient.builder().endpoint(endpoint).credentials(username, password).build();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
                while (process.isAlive() && System.nanoTime() < deadline) {
                    try {
                        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) return;
                        throw new IllegalStateException("The owned bucket unexpectedly existed before its first upload");
                    } catch (io.minio.errors.MinioException | java.io.IOException notReady) {
                        Thread.sleep(100);
                    }
                }
                throw new IllegalStateException("Owned MinIO did not become ready");
            } finally { client.close(); }
        } catch (Exception failure) {
            close();
            throw new IllegalStateException("Cannot start isolated attachment storage", failure);
        }
    }

    private static int port() throws Exception {
        try (var reservation = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { return reservation.getLocalPort(); }
    }

    @Override public synchronized void close() {
        if (process != null && process.isAlive()) {
            process.destroy();
            try { if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
        }
        if (shutdown != null && Thread.currentThread() != shutdown) {
            try { Runtime.getRuntime().removeShutdownHook(shutdown); }
            catch (IllegalStateException shuttingDown) { /* JVM shutdown already owns the hook. */ }
        }
    }
}
