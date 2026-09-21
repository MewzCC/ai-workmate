package com.aiworkmate.agent.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database-independent deployment gate for the code-owned Agent tool boundary.
 * Real PostgreSQL tests still validate the executed schema; this test prevents
 * them from becoming the only place that detects an omitted tool or permission seed.
 */
class AgentToolMigrationManifestTest {
    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");

    @Test
    void everyCodeOwnedToolHasAPlatformContractAndIndependentPermissionSeed() throws IOException {
        List<MigrationSource> migrations;
        try (var paths = Files.list(MIGRATION_DIRECTORY)) {
            migrations = paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .map(this::read)
                    .toList();
        }

        assertThat(migrations).isNotEmpty();
        for (ToolCode tool : ToolCode.values()) {
            assertThat(migrations.stream().anyMatch(migration -> migration.seedsTool(tool.code())))
                    .as("平台工具契约缺少 Flyway 种子: %s", tool.code())
                    .isTrue();
            assertThat(migrations.stream().anyMatch(migration -> migration.seedsPermission(tool.code())))
                    .as("工具实时权限缺少 Flyway 种子: agent:tool:%s", tool.code())
                    .isTrue();
        }
    }

    private MigrationSource read(Path path) {
        try {
            return new MigrationSource(path.getFileName().toString(), Files.readString(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read migration: " + path, exception);
        }
    }

    private record MigrationSource(String name, String sql) {
        boolean seedsTool(String toolCode) {
            return containsToolCode(toolCode) && sql.toLowerCase().contains("insert into agent_tool");
        }

        boolean seedsPermission(String toolCode) {
            if (sql.contains("'agent:tool:" + toolCode + "'")) return true;
            String compact = sql.replaceAll("\\s+", "").toLowerCase();
            return containsToolCode(toolCode)
                    && (compact.contains("'agent:tool:'||tool_code")
                    || compact.contains("concat('agent:tool:',tool_code)"));
        }

        private boolean containsToolCode(String toolCode) {
            return sql.contains("'" + toolCode + "'");
        }
    }
}
