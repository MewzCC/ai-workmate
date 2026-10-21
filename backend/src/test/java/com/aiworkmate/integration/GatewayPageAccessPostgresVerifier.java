package com.aiworkmate.integration;

import com.aiworkmate.agent.gateway.GatewayExecutionSnapshotMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the production snapshot mapper; the caller owns and removes the isolated test schema. */
final class GatewayPageAccessPostgresVerifier {
    private GatewayPageAccessPostgresVerifier() { }

    static void verify(String url, String username, String password, String schema) {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenantId = jdbc.queryForObject("SELECT id FROM tenant WHERE code = 'DEFAULT'", Long.class);
        Long userId = jdbc.queryForObject("""
                INSERT INTO app_user(username, password, email, tenant_id)
                VALUES ('gateway-page-verifier', 'test-only', 'gateway-page@example.invalid', ?) RETURNING id
                """, Long.class, tenantId);
        Long taskId = jdbc.queryForObject("""
                INSERT INTO agent_task(task_no, tenant_id, user_id, page_id, input, page_context,
                    plan, plan_hash, status, trace_id)
                VALUES (?, ?, ?, 'ai-workspace', 'query', '{}'::jsonb,
                    '{"planVersion":1,"steps":[]}'::jsonb, 'sha256:plan', 'RUNNING', 'gateway-page') RETURNING id
                """, Long.class, UUID.randomUUID().toString(), tenantId, userId);
        Long stepId = jdbc.queryForObject("""
                INSERT INTO agent_task_step(task_id, sequence_no, tool_code, tool_version, schema_hash,
                    args, args_hash, risk_level, status, trace_id)
                VALUES (?, 1, 'todo.query', '1.0.0', 'sha256:schema', '{}'::jsonb,
                    'sha256:args', 'L0', 'RUNNING', 'gateway-page') RETURNING id
                """, Long.class, taskId);

        var configuration = new Configuration(new Environment("gateway-page-test", new JdbcTransactionFactory(), datasource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(GatewayExecutionSnapshotMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            var mapper = session.getMapper(GatewayExecutionSnapshotMapper.class);
            var snapshot = mapper.selectSnapshot(stepId);
            assertThat(snapshot.getPageId()).isEqualTo("ai-workspace");
            assertThat(snapshot.getTaskId()).isEqualTo(taskId);
            assertThat(snapshot.getTenantId()).isEqualTo(tenantId);
            assertThat(snapshot.getUserId()).isEqualTo(userId);
            assertThat(mapper.selectSnapshot(-1L)).isNull();
        }
    }
}
