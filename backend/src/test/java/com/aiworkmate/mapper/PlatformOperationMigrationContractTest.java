package com.aiworkmate.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformOperationMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V202609221600__platform_operation_observability.sql");

    @Test
    void unifiesHumanAgentAndSystemOperationsWithoutPersistingRequestPayloads() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS platform_operation_log")
                .contains("CREATE OR REPLACE VIEW runtime_log_view")
                .contains("'HUMAN'::VARCHAR(16) AS actor_type")
                .contains("'AI_AGENT'::VARCHAR(16) AS actor_type")
                .contains("'SYSTEM'::VARCHAR(16) AS actor_type")
                .contains("FROM agent_tool_invocation invocation")
                .doesNotContain("request_body")
                .doesNotContain("authorization")
                .doesNotContain("cookie")
                .doesNotContain("DROP VIEW");
    }
}
