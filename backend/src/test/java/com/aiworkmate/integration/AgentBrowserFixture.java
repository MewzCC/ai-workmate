package com.aiworkmate.integration;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.assertj.core.api.Assertions.assertThat;

/** Owns browser/Vite descendants and passes only synthetic session data to the driver. */
final class AgentBrowserFixture {
    private AgentBrowserFixture() { }

    static int reserveUiPort() {
        try (var reservation = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return reservation.getLocalPort();
        } catch (IOException unavailable) {
            throw new IllegalStateException("Cannot reserve the owned browser test port");
        }
    }

    static void run(String schema, int apiPort, int uiPort, Map<String, String> session) throws Exception {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        Path log = Path.of("target", "agent-it", schema, "browser.log").toAbsolutePath();
        Files.createDirectories(log.getParent());
        var launcher = new ProcessBuilder("node", repository.resolve("scripts/verify-agent-browser.mjs").toString())
                .directory(repository.resolve("fonted-oa").toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        var original = System.getenv();
        launcher.environment().clear();
        for (String name : List.of("Path", "PATH", "SystemRoot", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP",
                "USERPROFILE", "LOCALAPPDATA", "APPDATA", "HOME", "PLAYWRIGHT_BROWSERS_PATH")) {
            if (original.containsKey(name)) launcher.environment().put(name, original.get(name));
        }
        launcher.environment().putAll(session);
        launcher.environment().put("AGENT_BROWSER_API_PORT", Integer.toString(apiPort));
        launcher.environment().put("AGENT_BROWSER_UI_PORT", Integer.toString(uiPort));
        Process browser = launcher.start();
        try {
            assertThat(browser.waitFor(210, TimeUnit.SECONDS)).as("owned browser completion budget").isTrue();
            Files.readAllLines(log).stream().filter(line -> line.startsWith("Browser acceptance:")).forEach(System.out::println);
            assertThat(browser.exitValue()).as("browser acceptance exit code; inspect only the private local log").isZero();
        } finally {
            if (browser.isAlive()) {
                var owned = new ArrayList<>(browser.descendants().toList());
                owned.add(browser.toHandle());
                owned.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroy);
                try {
                    CompletableFuture.allOf(owned.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
                } catch (TimeoutException timeout) {
                    // Escalate only captured descendants of this still-owned process.
                } finally {
                    owned.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                }
                CompletableFuture.allOf(owned.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
                assertThat(owned).noneMatch(ProcessHandle::isAlive);
            }
        }
    }
}
