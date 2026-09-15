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
        MeetingBookingPostgresVerifier.verify(databaseUrl, databaseUsername, databasePassword, emptySchema);
        AttendanceReissuePostgresVerifier.verify(databaseUrl, databaseUsername, databasePassword, emptySchema);
        NotificationMarkReadPostgresVerifier.verify(databaseUrl, databaseUsername, databasePassword, emptySchema);
        LeaveWithdrawalPostgresVerifier.verify(databaseUrl, databaseUsername, databasePassword, emptySchema);
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
        assertThat(queryCount(upgradeSchema, """
                SELECT COUNT(*) FROM integration_endpoint
                WHERE endpoint_code = 'LEGACY-API-001' AND status = 'DRAFT'
                """)).isOne();
        assertThat(queryCount(upgradeSchema, """
                SELECT COUNT(*) FROM agent_page_action_policy
                WHERE page_id = 'dashboard' AND tool_code = 'todo.query' AND enabled = FALSE
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
                        'budget_plan', 'budget_transaction', 'integration_endpoint', 'integration_invocation',
                        'agent_page_action_policy', 'integration_replay_job')
                    """)).isEqualTo(23);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('approval:manage', 'hr:manage', 'asset:write',
                      'meeting:book', 'visitor:register', 'seal:register', 'dictionary:manage',
                      'tenant:config:manage', 'agent-permission:manage', 'supplier:manage', 'contract:manage',
                      'budget:manage', 'integration:endpoint:manage', 'integration:endpoint:execute',
                      'page-action:manage', 'runtime-log:read', 'integration:replay:read',
                      'integration:replay:execute')
                    """)).isEqualTo(18);
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
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_route
                    WHERE route_key = 'api-center' AND component_key = 'API_CENTER'
                    """)).isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_route
                    WHERE route_key = 'page-actions' AND component_key = 'PAGE_ACTIONS'
                    """)).isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_route
                    WHERE route_key = 'runtime-logs' AND component_key = 'RUNTIME_LOGS'
                    """)).isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_route
                    WHERE route_key = 'sandbox-replay' AND component_key = 'SANDBOX_REPLAY'
                    """)).isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*)
                    FROM (VALUES
                        ('dictionary', 'DICTIONARY'),
                        ('tenant-config', 'TENANT_CONFIG'),
                        ('data-permission', 'DATA_PERMISSION'),
                        ('ai-permission', 'AI_PERMISSION'),
                        ('suppliers', 'SUPPLIER'),
                        ('contracts', 'CONTRACT'),
                        ('expense', 'EXPENSE'),
                        ('budget', 'BUDGET'),
                        ('api-center', 'API_CENTER'),
                        ('page-actions', 'PAGE_ACTIONS'),
                        ('runtime-logs', 'RUNTIME_LOGS'),
                        ('sandbox-replay', 'SANDBOX_REPLAY')
                    ) AS planned(route_key, component_key)
                    JOIN rbac_route route
                      ON route.route_key = planned.route_key
                     AND route.component_key = planned.component_key
                     AND route.route_type = 'PAGE'
                     AND route.enabled = TRUE
                    """)).isEqualTo(12);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_route
                    WHERE route_type = 'PAGE' AND enabled = TRUE
                      AND (component_key = 'WORKBENCH_MODULE'
                        OR (component_key = 'DASHBOARD' AND route_key <> 'dashboard'))
                    """)).as("已启用业务页面不得回退到通用台账或驾驶舱占位组件").isZero();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_route
                    WHERE route_type = 'PAGE' AND enabled = TRUE
                    """)).as("R4 浏览器回归清单必须覆盖全部已启用页面").isEqualTo(41);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_page_action_policy
                    WHERE page_id IN ('todo-list', 'message-center')
                    """)).as("Agent 页面策略必须使用正式路由标识").isZero();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL
                      AND code = 'approval.configuration.query'
                      AND handler_version = '1.0.0'
                      AND risk_level = 'L0'
                      AND data_scope_policy = 'TENANT_SCOPED'
                      AND side_effect = 'NONE'
                      AND enabled = TRUE
                    """)).as("审批配置 Agent 工具必须以只读租户范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code = 'agent:tool:approval.configuration.query'
                    """)).as("审批配置 Agent 工具必须具备独立实时权限").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL
                      AND code = 'approval.task.query'
                      AND handler_version = '1.0.0'
                      AND risk_level = 'L0'
                      AND data_scope_policy = 'TENANT_SCOPED'
                      AND side_effect = 'NONE'
                      AND enabled = TRUE
                    """)).as("审批中心 Agent 工具必须以只读租户范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code = 'agent:tool:approval.task.query'
                    """)).as("审批中心 Agent 工具必须具备独立实时权限").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL
                      AND code = 'hr.organization.query'
                      AND handler_version = '1.0.0'
                      AND risk_level = 'L0'
                      AND data_scope_policy = 'TENANT_SCOPED'
                      AND side_effect = 'NONE'
                      AND enabled = TRUE
                    """)).as("组织架构 Agent 工具必须以只读租户范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code = 'agent:tool:hr.organization.query'
                    """)).as("组织架构 Agent 工具必须具备独立实时权限").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'hr.employee.query'
                      AND handler_version = '1.0.0' AND risk_level = 'L0'
                      AND data_scope_policy = 'TENANT_SCOPED' AND side_effect = 'NONE' AND enabled = TRUE
                    """)).as("员工档案 Agent 工具必须以只读租户范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission WHERE code = 'agent:tool:hr.employee.query'
                    """)).as("员工档案 Agent 工具必须具备独立实时权限").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'hr.change.query' AND handler_version = '1.0.0'
                      AND risk_level = 'L0' AND data_scope_policy = 'TENANT_SCOPED'
                      AND side_effect = 'NONE' AND enabled = TRUE
                    """)).as("入转调离 Agent 工具必须以只读租户范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission WHERE code = 'agent:tool:hr.change.query'
                    """)).as("入转调离 Agent 工具必须具备独立实时权限").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'asset.query' AND handler_version = '1.0.0'
                      AND risk_level = 'L0' AND data_scope_policy = 'TENANT_SCOPED'
                      AND side_effect = 'NONE' AND enabled = TRUE
                    """)).as("资产台账 Agent 工具必须以只读租户范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission WHERE code = 'agent:tool:asset.query'
                    """)).as("资产台账 Agent 工具必须具备独立实时权限").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'meeting.query' AND handler_version = '1.0.0'
                      AND risk_level = 'L0' AND data_scope_policy = 'TENANT_SCOPED'
                      AND side_effect = 'NONE' AND enabled = TRUE
                    """)).as("会议室 Agent 工具必须以只读租户范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission WHERE code = 'agent:tool:meeting.query'
                    """)).as("会议室 Agent 工具必须具备独立实时权限").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'meeting.book' AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:08abfb0571d3c4b1576423f7aae0849039062dccca5025efef23e9cb0e88f276'
                      AND risk_level = 'L1' AND data_scope_policy = 'SELF'
                      AND retry_policy = 'BUSINESS_IDEMPOTENT' AND side_effect = 'SINGLE_WRITE'
                      AND confirmation_policy = 'EXPLICIT' AND enabled = TRUE
                    """)).as("会议室预约写工具必须以冻结的本人原子写契约存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('meeting:book', 'agent:tool:meeting.book')
                    """)).as("会议室预约写工具必须具备业务与工具两层实时权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'notification.markRead' AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:b46cdf75d92a2dbf816572096b8b1eb184ce2040ed702150a6e371d08ba1b487'
                      AND risk_level = 'L1' AND data_scope_policy = 'SELF'
                      AND retry_policy = 'BUSINESS_IDEMPOTENT' AND side_effect = 'SINGLE_WRITE'
                      AND confirmation_policy = 'EXPLICIT' AND enabled = TRUE
                    """)).as("消息已读写工具必须以冻结的本人单条写契约存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'leave.withdraw' AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:8d093edc7124a4cd99ba92352223c2934524fe4a022d63916dc33513844571a9'
                      AND risk_level = 'L1' AND data_scope_policy = 'SELF'
                      AND retry_policy = 'NEVER' AND side_effect = 'SINGLE_WRITE'
                      AND confirmation_policy = 'EXPLICIT' AND enabled = TRUE
                    """)).as("请假撤回工具必须以本人单写且禁止自动重试契约存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('leave:withdraw', 'agent:tool:leave.withdraw')
                    """)).as("请假撤回必须具备业务与工具两层权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('notification:read:self', 'agent:tool:notification.markRead')
                    """)).as("消息已读写工具必须具备业务与工具两层实时权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = 'meeting_booking'
                      AND column_name = 'agent_operation_key'
                    """)).as("会议预约必须持久化 Agent 领域幂等键").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = current_schema() AND tablename = 'meeting_booking'
                      AND indexname = 'ux_meeting_booking_agent_operation'
                    """)).as("会议预约 Agent 幂等键必须具备唯一索引").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'attendance.query' AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:4ff357d3836da753f0c32d3774ea04ba3cf83d1c6b8708de548e8ff711348d60'
                      AND risk_level = 'L0' AND data_scope_policy = 'TENANT_SCOPED'
                      AND side_effect = 'NONE' AND enabled = TRUE
                    """)).as("考勤 Agent 工具必须以冻结契约的只读租户范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('attendance:read', 'agent:tool:attendance.query')
                    """)).as("考勤 Agent 工具必须具备业务与工具两层实时权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'attendance.reissue.apply' AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:bb3f7af3920e01290f14107d053425e8d986f988069eaa20031455ce49cce760'
                      AND risk_level = 'L1' AND data_scope_policy = 'SELF'
                      AND retry_policy = 'BUSINESS_IDEMPOTENT' AND side_effect = 'SINGLE_WRITE'
                      AND confirmation_policy = 'EXPLICIT' AND enabled = TRUE
                    """)).as("补卡申请工具必须以冻结的本人原子写契约存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('attendance:reissue:apply', 'agent:tool:attendance.reissue.apply')
                    """)).as("补卡申请工具必须具备业务与工具两层实时权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = 'attendance_reissue'
                      AND column_name = 'agent_operation_key'
                    """)).as("补卡申请必须持久化 Agent 领域幂等键").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = current_schema() AND tablename = 'attendance_reissue'
                      AND indexname = 'ux_attendance_reissue_agent_operation'
                    """)).as("补卡申请 Agent 幂等键必须具备唯一索引").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'approval.application.createDraft'
                      AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:0b91ec92030a3bdb99baac22dce2b1e7c3a239829740fe9ad174b24ecf340424'
                      AND risk_level = 'L1' AND data_scope_policy = 'SELF'
                      AND retry_policy = 'BUSINESS_IDEMPOTENT' AND side_effect = 'SINGLE_WRITE'
                      AND confirmation_policy = 'EXPLICIT' AND enabled = TRUE
                    """)).as("通用审批草稿工具必须以冻结的本人原子写契约存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('approval:create', 'agent:tool:approval.application.createDraft')
                    """)).as("通用审批草稿工具必须具备业务与工具两层实时权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'approval.application.submitDraft'
                      AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:edbe7ad52b50a98e339f5e7cf83b572c26d3b71ddccb1f50aa11496fbed80426'
                      AND risk_level = 'L1' AND data_scope_policy = 'SELF'
                      AND retry_policy = 'NEVER' AND side_effect = 'SINGLE_WRITE'
                      AND confirmation_policy = 'EXPLICIT' AND enabled = TRUE
                    """)).as("通用审批草稿提交工具必须以本人单写且禁止自动重试契约存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('approval:submit', 'agent:tool:approval.application.submitDraft')
                    """)).as("通用审批草稿提交工具必须具备业务与工具两层实时权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = 'approval_application'
                      AND column_name = 'agent_operation_key'
                    """)).as("通用审批草稿必须持久化 Agent 领域幂等键").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = current_schema() AND tablename = 'approval_application'
                      AND indexname = 'ux_approval_application_agent_operation'
                    """)).as("通用审批草稿 Agent 幂等键必须具备唯一索引").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'visitor.query' AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:aa6f923d24734c45f3022026885641deb9b74abbe6e9061394234e5cc739a3aa'
                      AND risk_level = 'L0' AND data_scope_policy = 'SELF'
                      AND required_permissions = '["visitor:read:self"]'::jsonb
                      AND side_effect = 'NONE' AND enabled = TRUE
                    """)).as("访客预约 Agent 工具必须以冻结契约的本人范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND code = 'seal.query' AND handler_version = '1.0.0'
                      AND schema_hash = 'sha256:9f896eeb4041562e8c0a7d33ed8117af6c9be6f91d71ce3c763a59d3ce3c0175'
                      AND risk_level = 'L0' AND data_scope_policy = 'SELF'
                      AND required_permissions = '["seal:read:self"]'::jsonb
                      AND side_effect = 'NONE' AND enabled = TRUE
                    """)).as("印章用印 Agent 工具必须以冻结契约的本人范围种子存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('agent:tool:visitor.query', 'agent:tool:seal.query')
                    """)).as("访客与用印 Agent 工具必须具备独立实时权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND enabled = TRUE AND side_effect = 'NONE'
                      AND (code, schema_hash, data_scope_policy) IN (
                        ('expense.query','sha256:054bddbc37bd581b6feae1ad51fc702f1b5c48a4b93575b174eefbbe185f74c8','SELF'),
                        ('budget.query','sha256:7d700b1b7b6c3556ed14833b9d7d1a4bb81e461a305afb108f4b75e651dd2760','TENANT_SCOPED'),
                        ('contract.query','sha256:51c12cd5d1f1b3297dfce9fe7d11e0f7d603766dfbd3aeb1fb0584f775acb02b','TENANT_SCOPED'),
                        ('supplier.query','sha256:7adf543de65a87fa28bbd4713d1c42f44e3522a90bacd00c12e178917d77ebe0','TENANT_SCOPED'))
                    """)).as("四个财务页面 Agent 工具必须以冻结契约存在").isEqualTo(4);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('expense:read:self','agent:tool:expense.query','agent:tool:budget.query',
                                   'agent:tool:contract.query','agent:tool:supplier.query')
                    """)).as("财务 Agent 工具必须具备业务与工具两层实时权限").isEqualTo(5);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND enabled = TRUE AND side_effect = 'NONE'
                      AND (code, schema_hash, data_scope_policy) IN (
                        ('integration.endpoint.query','sha256:77b6949a8aca42cdd8c7776e51e64e2fa4bf52c64bb4d950e5d61e2c19eadd22','TENANT_SCOPED'),
                        ('pageAction.query','sha256:7f6c91f43b7a3416e8824b064421ec1be233c0a952fed1b068dc255c8e9cdd96','TENANT_SCOPED'),
                        ('runtimeLog.query','sha256:d79d49ccc330312907ff310bb96264640e938dbedd5df7c5054fe93357cadcac','TENANT_SCOPED'),
                        ('sandboxReplay.query','sha256:733b65aba2e3dc720f2d17d6af7be1ea434b93fc7f219c9a551fd4f08e551978','TENANT_SCOPED'))
                    """)).as("四个平台运维页面 Agent 工具必须以冻结契约存在").isEqualTo(4);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('integration:endpoint:read','page-action:read',
                        'agent:tool:integration.endpoint.query','agent:tool:pageAction.query',
                        'agent:tool:runtimeLog.query','agent:tool:sandboxReplay.query')
                    """)).as("平台运维 Agent 工具必须具备业务与工具两层实时权限").isEqualTo(6);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND enabled=TRUE AND side_effect='NONE'
                      AND (code,schema_hash,data_scope_policy) IN (
                        ('accessGovernance.query','sha256:d622f57307336098c25b779333ff842a874adcbf1880ae687c08276d3c6bbc61','TENANT_SCOPED'),
                        ('dataPermission.query','sha256:858c22c5e8947762eda39892caf2fbf962282c21d94945491daf34ebde3f6c21','TENANT_SCOPED'),
                        ('aiPermission.query','sha256:270d6da876eea0376fddf296219842f25f2bdc594ec61023ac365006cfc7ae87','TENANT_SCOPED'))
                    """)).as("三个安全治理 Agent 工具必须以冻结契约存在").isEqualTo(3);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('agent:tool:accessGovernance.query','agent:tool:dataPermission.query','agent:tool:aiPermission.query')
                    """)).as("安全治理 Agent 工具必须具备独立实时权限").isEqualTo(3);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND enabled=TRUE AND side_effect='NONE'
                      AND (code,schema_hash,data_scope_policy) IN (
                        ('audit.query','sha256:c20374b208d69def5365f321daa3c9d80c8c7a54ed7ab0a1b0cd10ade022ab04','TENANT_SCOPED'),
                        ('tenantConfiguration.query','sha256:884afa367781930d66fdafc2b64205710c17b546099610be43be6e960531cba6','TENANT_SCOPED'),
                        ('dictionary.query','sha256:d4cc084b963a3e48f1a63c8ab6b988c1f4a8ce361db6b4a74d28867cb821ff92','TENANT_SCOPED'),
                        ('systemCapability.query','sha256:4e80069fa10fe0a961e030bd54c85a87f69238ed9ddab94675085576bcefae88','TENANT_SCOPED'))
                    """)).as("四个运行治理 Agent 工具必须以冻结契约存在").isEqualTo(4);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('agent:tool:audit.query','agent:tool:tenantConfiguration.query',
                        'agent:tool:dictionary.query','agent:tool:systemCapability.query')
                    """)).as("运行治理 Agent 工具必须具备独立实时权限").isEqualTo(4);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM agent_tool
                    WHERE tenant_id IS NULL AND enabled=TRUE AND side_effect='NONE'
                      AND code='agentTask.mine.query'
                      AND schema_hash='sha256:1d4697a6ea535d206068ea1b0e6d8605c2c8f7d80686f90602131dde3df87658'
                      AND data_scope_policy='SELF'
                    """)).as("AI 任务中心本人查询工具必须以冻结契约存在").isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM rbac_permission
                    WHERE code IN ('agent:task:read','agent:tool:agentTask.mine.query')
                    """)).as("AI 任务中心工具必须具备业务与工具两层实时权限").isEqualTo(2);
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM information_schema.views
                    WHERE table_schema = current_schema() AND table_name = 'runtime_log_view'
                    """)).isOne();
            assertThat(count(statement, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = 'integration_invocation'
                      AND column_name = 'trace_id'
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
            int insertedApi = statement.executeUpdate("""
                    INSERT INTO workbench_record(
                        tenant_id, module_key, record_code, title, category, status, owner, details,
                        version, deleted, created_by, updated_by, created_at, updated_at
                    )
                    SELECT tenant.id, 'api-center', 'legacy-api-001', '历史接口登记', 'GET', 'ACTIVE',
                           '系统组', '旧版接口说明', 1, FALSE, user_account.id, user_account.id,
                           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM tenant
                    JOIN app_user user_account ON user_account.username = 'p1-supplier-migrator'
                    WHERE tenant.code = 'DEFAULT'
                    """);
            assertThat(insertedApi).isOne();
            int insertedPageAction = statement.executeUpdate("""
                    INSERT INTO workbench_record(
                        tenant_id, module_key, record_code, title, category, status, owner, details,
                        version, deleted, created_by, updated_by, created_at, updated_at
                    )
                    SELECT tenant.id, 'page-actions', 'dashboard:todo.query', '历史待办查询动作',
                           'AGENT_TOOL', 'DISABLED', '系统组', '旧版页面动作开关', 2, FALSE,
                           user_account.id, user_account.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM tenant
                    JOIN app_user user_account ON user_account.username = 'p1-supplier-migrator'
                    WHERE tenant.code = 'DEFAULT'
                    """);
            assertThat(insertedPageAction).isOne();
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
