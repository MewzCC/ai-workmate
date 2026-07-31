package com.aiworkmate.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class InitSqlIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ai_workmate_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Test
    void shouldInitializeEmptyDatabaseAndRemainIdempotent() throws Exception {
        String script = Files.readString(
                Path.of("src/main/resources/db/init.sql"), StandardCharsets.UTF_8);
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute(script);
            statement.execute(script);
            try (var result = statement.executeQuery("""
                    SELECT t.code,
                           (SELECT COUNT(*) FROM workflow_definition) AS definitions,
                           (SELECT COUNT(*) FROM rbac_route WHERE component_key = 'LEAVE_FORM') AS leave_routes,
                           (SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector') AS vector_extensions,
                           (SELECT COUNT(*) FROM information_schema.columns
                            WHERE table_name = 'knowledge_chunk'
                              AND column_name = 'embedding'
                              AND udt_name = 'vector') AS vector_columns
                    FROM tenant t
                    WHERE t.code = 'DEFAULT'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("code")).isEqualTo("DEFAULT");
                assertThat(result.getInt("definitions")).isEqualTo(1);
                assertThat(result.getInt("leave_routes")).isEqualTo(1);
                assertThat(result.getInt("vector_extensions")).isEqualTo(1);
                assertThat(result.getInt("vector_columns")).isEqualTo(1);
            }
        }
    }
}
