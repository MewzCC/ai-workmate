package com.aiworkmate.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 真实 PostgreSQL 迁移门禁。
 *
 * <p>该类使用 IT 后缀，不进入普通 Surefire 扫描；必须由
 * {@code scripts/verify-p1-postgres.ps1} 显式执行。入口缺少数据库参数时直接失败，
 * 不允许用条件注解或 Assumption 静默跳过。</p>
 */
class P1PostgresMigrationIT {

    private static String databaseUrl;
    private static String databaseUsername;
    private static String databasePassword;
    private static String emptySchema;
    private static String upgradeSchema;

    @BeforeAll
    static void requireRealPostgres() throws Exception {
        databaseUrl = required("p1.test.db.url");
        databaseUsername = required("p1.test.db.username");
        databasePassword = System.getProperty("p1.test.db.password", "");
        assertThat(databaseUrl).startsWith("jdbc:postgresql:");

        try (Connection connection = DriverManager.getConnection(
                databaseUrl, databaseUsername, databasePassword)) {
            assertThat(connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT))
                    .contains("postgresql");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        emptySchema = "p1_empty_" + suffix;
        upgradeSchema = "p1_upgrade_" + suffix;
    }

    @AfterAll
    static void cleanIsolatedSchemas() {
        clean(emptySchema);
        clean(upgradeSchema);
    }

    @Test
    void emptyAndExistingSchemasMigrateValidateAndRestartWithoutErrors() throws Exception {
        Flyway empty = flyway(emptySchema, null);
        var emptyResult = empty.migrate();
        assertThat(emptyResult.migrationsExecuted).isGreaterThan(0);
        assertThat(empty.validateWithResult().validationSuccessful).isTrue();
        assertP1Schema(emptySchema);

        Flyway legacy = flyway(upgradeSchema, MigrationVersion.fromVersion("4"));
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(4);

        Flyway upgraded = flyway(upgradeSchema, null);
        assertThat(upgraded.migrate().migrationsExecuted).isGreaterThan(0);
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertP1Schema(upgradeSchema);

        Flyway restarted = flyway(emptySchema, null);
        assertThat(restarted.migrate().migrationsExecuted).isZero();
        assertThat(restarted.validateWithResult().validationSuccessful).isTrue();
        assertNoDuplicateMigrationVersions(emptySchema);
    }

    private static Flyway flyway(String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(databaseUrl, databaseUsername, databasePassword)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .cleanDisabled(false)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"));
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static void assertP1Schema(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                databaseUrl, databaseUsername, databasePassword);
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO \"" + schema + "\"");
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name IN ('approval_application', 'employee_change',
                        'employee_document', 'asset_operation', 'meeting_booking',
                        'visitor_booking', 'seal_usage_document', 'user_setting')
                    """)).isEqualTo(8);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('approval:manage', 'hr:manage', 'asset:write',
                      'meeting:book', 'visitor:register', 'seal:register')
                    """)).isEqualTo(6);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM flyway_schema_history WHERE success
                    """)).isGreaterThan(30);
        }
    }

    private static void assertNoDuplicateMigrationVersions(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                databaseUrl, databaseUsername, databasePassword);
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO \"" + schema + "\"");
            assertThat(count(statement, """
                    SELECT COUNT(*) - COUNT(DISTINCT version)
                    FROM flyway_schema_history WHERE success AND version IS NOT NULL
                    """)).isZero();
        }
    }

    private static long count(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static String required(String property) {
        String value = System.getProperty(property);
        assertThat(value)
                .withFailMessage("必须显式提供 -D%s，真实 PostgreSQL 测试不允许静默跳过", property)
                .isNotBlank();
        return value;
    }

    private static void clean(String schema) {
        if (schema == null || !schema.matches("p1_(empty|upgrade)_[a-f0-9]{12}")) return;
        try {
            flyway(schema, null).clean();
        } catch (RuntimeException ignored) {
            // 测试失败时保留原始异常；隔离 schema 名不会触及业务 schema。
        }
    }
}
