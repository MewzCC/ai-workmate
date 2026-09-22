package com.aiworkmate.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the human-operation branch of the unified runtime log against PostgreSQL. */
final class PlatformOperationLogPostgresVerifier {
    private PlatformOperationLogPostgresVerifier() {
    }

    static void verify(String url, String username, String password, String schema) {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenantId = jdbc.queryForObject(
                "SELECT id FROM tenant WHERE code = 'DEFAULT'", Long.class);
        Long userId = jdbc.queryForObject("""
                INSERT INTO app_user(username, display_name, password, email, role, status, tenant_id)
                VALUES ('operation-log-verifier', '审计验证用户', 'test-only',
                        'operation-log-verifier@example.invalid', 'EMPLOYEE', 1, ?)
                RETURNING id
                """, Long.class, tenantId);

        jdbc.update("""
                INSERT INTO platform_operation_log(
                    tenant_id, user_id, actor_label, event_type, http_method, request_path,
                    outcome, status_code, duration_ms, client_ip, user_agent,
                    request_id, trace_id, started_at, completed_at
                ) VALUES (?, ?, '审计验证用户', 'LOGIN', 'PASSWORD', '/api/auth/login',
                          'SUCCEEDED', 200, 12, '127.0.0.1', 'Postgres Verifier',
                          'operation-request', 'operation-trace', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, tenantId, userId);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT source, actor_type, event_type, operator_id, operation, outcome,
                       client_ip, user_agent
                FROM runtime_log_view
                WHERE tenant_id = ? AND reference_code = 'operation-request'
                """, tenantId);
        assertThat(row).containsEntry("source", "HUMAN")
                .containsEntry("actor_type", "HUMAN")
                .containsEntry("event_type", "LOGIN")
                .containsEntry("outcome", "SUCCEEDED")
                .containsEntry("client_ip", "127.0.0.1")
                .containsEntry("user_agent", "Postgres Verifier");
        assertThat(((Number) row.get("operator_id")).longValue()).isEqualTo(userId);
        assertThat(row.get("operation")).isEqualTo("PASSWORD /api/auth/login");

        Integer sensitiveColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'platform_operation_log'
                  AND column_name IN ('request_body', 'response_body', 'authorization', 'cookie', 'token')
                """, Integer.class);
        assertThat(sensitiveColumns).isZero();

        String viewDefinition = jdbc.queryForObject(
                "SELECT pg_get_viewdef('runtime_log_view'::regclass, true)", String.class);
        assertThat(viewDefinition).contains("platform_operation_log")
                .contains("agent_tool_invocation")
                .contains("AI_AGENT");
    }
}
