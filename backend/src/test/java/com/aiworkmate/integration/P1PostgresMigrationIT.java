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
        Flyway restartedEmpty = flyway(emptySchema, null);
        assertThat(restartedEmpty.migrate().migrationsExecuted).isZero();
        assertThat(restartedEmpty.validateWithResult().validationSuccessful).isTrue();
        assertNoDuplicateMigrationVersions(emptySchema);

        // pgvector is a database级扩展，同一数据库中的两个隔离 schema 不能各自安装一份。
        // 空库门禁完成后先清理，再验证既有库升级，避免测试 schema 之间相互污染搜索路径。
        empty.clean();
        emptySchema = null;

        Flyway legacy = flyway(upgradeSchema, MigrationVersion.fromVersion("4"));
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(4);

        Flyway beforeSupplier = flyway(upgradeSchema, MigrationVersion.fromVersion("202609101100"));
        assertThat(beforeSupplier.migrate().migrationsExecuted).isGreaterThan(0);
        insertLegacySupplierRecord(upgradeSchema);

        Flyway upgraded = flyway(upgradeSchema, null);
        assertThat(upgraded.migrate().migrationsExecuted).isGreaterThan(0);
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertP1Schema(upgradeSchema);
        assertThat(queryCount(upgradeSchema, """
                SELECT COUNT(*) FROM supplier
                WHERE supplier_code = 'LEGACY-SUP-001' AND status = 'ACTIVE'
                """)).isOne();
        assertThat(queryCount(upgradeSchema, """
                SELECT COUNT(*) FROM business_contract
                WHERE contract_code = 'LEGACY-CONTRACT-001' AND status = 'ACTIVE'
                """)).isOne();
        assertThat(queryCount(upgradeSchema, """
                SELECT COUNT(*) FROM budget_plan
                WHERE budget_code = 'LEGACY-BUDGET-001' AND status = 'ACTIVE'
                """)).isOne();

        Flyway restartedUpgrade = flyway(upgradeSchema, null);
        assertThat(restartedUpgrade.migrate().migrationsExecuted).isZero();
        assertThat(restartedUpgrade.validateWithResult().validationSuccessful).isTrue();
        assertNoDuplicateMigrationVersions(upgradeSchema);
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
                        'visitor_booking', 'seal_usage_document', 'user_setting',
                        'data_dictionary_type', 'data_dictionary_item', 'data_dictionary_item_usage',
                        'tenant_configuration', 'tenant_configuration_history',
                        'supplier', 'supplier_status_history', 'business_contract', 'contract_event',
                        'budget_plan', 'budget_transaction')
                    """)).isEqualTo(19);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('approval:manage', 'hr:manage', 'asset:write',
                      'meeting:book', 'visitor:register', 'seal:register', 'dictionary:manage',
                      'tenant:config:manage', 'agent-permission:manage', 'supplier:manage', 'contract:manage',
                      'budget:manage')
                    """)).isEqualTo(12);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM flyway_schema_history WHERE success
                    """)).isGreaterThan(30);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM approval_process
                    WHERE process_key = 'expense-single-approval' AND status = 'ENABLED' AND deleted = FALSE
                    """)).isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_route
                    WHERE route_key = 'expense' AND component_key = 'EXPENSE'
                    """)).isOne();
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

    private static void insertLegacySupplierRecord(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO \"" + schema + "\"");
            int insertedUser = statement.executeUpdate("""
                    INSERT INTO app_user(username, display_name, password, email, role, status, tenant_id)
                    SELECT 'p1-supplier-migrator', '迁移测试用户', 'test-only',
                           'p1-supplier-migrator@example.invalid', 'SUPER_ADMIN', 1, tenant.id
                    FROM tenant
                    WHERE tenant.code = 'DEFAULT'
                    ON CONFLICT (username) DO NOTHING
                    """);
            assertThat(insertedUser).isOne();

            int insertedRecord = statement.executeUpdate("""
                    INSERT INTO workbench_record(
                        tenant_id, module_key, record_code, title, category, status, owner, details,
                        version, deleted, created_by, updated_by, created_at, updated_at
                    )
                    SELECT tenant.id, 'suppliers', 'legacy-sup-001', '历史供应商', 'SERVICE', 'ACTIVE',
                           '历史联系人', '历史风险备注', 2, FALSE, user_account.id, user_account.id,
                           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM tenant
                    JOIN app_user user_account ON user_account.username = 'p1-supplier-migrator'
                    WHERE tenant.code = 'DEFAULT'
                    """);
            assertThat(insertedRecord).isOne();
            int insertedContract = statement.executeUpdate("""
                    INSERT INTO workbench_record(
                        tenant_id, module_key, record_code, title, category, status, amount, owner, details,
                        version, deleted, created_by, updated_by, created_at, updated_at
                    )
                    SELECT tenant.id, 'contracts', 'legacy-contract-001', '历史采购合同', 'PURCHASE', 'ACTIVE',
                           120000, '历史相对方', '历史合同摘要', 1, FALSE, user_account.id, user_account.id,
                           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM tenant
                    JOIN app_user user_account ON user_account.username = 'p1-supplier-migrator'
                    WHERE tenant.code = 'DEFAULT'
                    """);
            assertThat(insertedContract).isOne();
            int insertedBudget = statement.executeUpdate("""
                    INSERT INTO workbench_record(
                        tenant_id, module_key, record_code, title, category, status, amount, owner, details,
                        version, deleted, created_by, updated_by, created_at, updated_at
                    )
                    SELECT tenant.id, 'budget', 'legacy-budget-001', '历史年度预算', 'ANNUAL', 'ACTIVE',
                           300000, '迁移测试用户', '历史预算说明', 1, FALSE, user_account.id, user_account.id,
                           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM tenant
                    JOIN app_user user_account ON user_account.username = 'p1-supplier-migrator'
                    WHERE tenant.code = 'DEFAULT'
                    """);
            assertThat(insertedBudget).isOne();
        }
    }

    private static long queryCount(String schema, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
             Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO \"" + schema + "\"");
            return count(statement, sql);
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
