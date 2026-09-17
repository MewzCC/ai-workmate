package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.entity.AssetLedger;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AssetLedgerMapper;
import com.aiworkmate.mapper.AssetOperationMapper;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.mapper.MeetingBookingMapper;
import com.aiworkmate.mapper.MeetingRoomMapper;
import com.aiworkmate.mapper.SealUsageMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.mapper.VisitorBookingMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.AdminAssetsServiceImpl;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.model.AssetAgentClaimCommand;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real PostgreSQL proof for one version-bound, idempotent Agent asset assignment. */
final class AssetClaimPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long department = jdbc.queryForObject(
                "SELECT id FROM department WHERE tenant_id=? ORDER BY id LIMIT 1", Long.class, tenant);
        Long operator = jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,tenant_id,department_id,status)
                VALUES ('asset-operator','test-only','asset-operator@example.invalid',?,?,1) RETURNING id
                """, Long.class, tenant, department);
        Long employee = jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,tenant_id,department_id,status)
                VALUES ('asset-employee','test-only','asset-employee@example.invalid',?,?,1) RETURNING id
                """, Long.class, tenant, department);

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(AssetLedgerMapper.class, AssetOperationMapper.class,
                AccessControlMapper.class, UserMapper.class, BusinessAuditLogMapper.class)) {
            configuration.addMapper(mapper);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(operator)).thenReturn(actor(operator, tenant));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));

        long assetId = insertAsset(tx, session.getMapper(AssetLedgerMapper.class), tenant, department, "A-CLAIM-1");
        var command = new AssetAgentClaimCommand(assetId, employee, 0, "新员工领用");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> claim = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.claimAssetAgent(
                        operator, command, "asset-claim-operation").version());
            };
            var first = executor.submit(claim);
            var second = executor.submit(claim);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM asset_operation
                WHERE agent_operation_key='asset-claim-operation'
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='ASSET_LEDGER' AND resource_id=? AND action='CLAIM'
                """, Integer.class, Long.toString(assetId))).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM asset_ledger WHERE id=?", String.class, assetId)).isEqualTo("IN_USE");
        assertThat(jdbc.queryForObject(
                "SELECT owner_user_id FROM asset_ledger WHERE id=?", Long.class, assetId)).isEqualTo(employee);

        assertThatThrownBy(() -> tx.execute(status -> service.claimAssetAgent(operator,
                new AssetAgentClaimCommand(assetId, employee, 0, "不同原因"), "asset-claim-operation")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_CONFLICT");
        var replay = tx.execute(status -> service.findAgentAssetClaim(
                operator, command, "asset-claim-operation"));
        assertThat(replay.isPresent()).isTrue();
        assertThat(replay.orElseThrow().version()).isEqualTo(1);

        long rollbackAsset = insertAsset(
                tx, session.getMapper(AssetLedgerMapper.class), tenant, department, "A-CLAIM-ROLLBACK");
        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingService = service(session, access, failingAudit);
        assertThatThrownBy(() -> tx.execute(status -> failingService.claimAssetAgent(operator,
                new AssetAgentClaimCommand(rollbackAsset, employee, 0, null), "asset-claim-rollback")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM asset_ledger WHERE id=?", String.class, rollbackAsset)).isEqualTo("IDLE");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM asset_operation WHERE agent_operation_key='asset-claim-rollback'
                """, Integer.class)).isZero();

        when(access.resolveActiveUser(operator)).thenReturn(new ResolvedUserAccess(operator, "operator",
                tenant + 100000, "SYSTEM_ADMIN", List.of("SYSTEM_ADMIN"), List.of("asset:claim"),
                List.of("ALL"), 1L));
        var crossTenantReplay = tx.execute(status -> service.findAgentAssetClaim(
                operator, command, "asset-claim-operation"));
        assertThat(crossTenantReplay.isEmpty()).isTrue();
        when(access.resolveActiveUser(operator)).thenReturn(new ResolvedUserAccess(operator, "operator",
                tenant, "SYSTEM_ADMIN", List.of("SYSTEM_ADMIN"), List.of(), List.of("ALL"), 1L));
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentAssetClaim(
                operator, command, "asset-claim-operation")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }

    private static ResolvedUserAccess actor(long userId, long tenantId) {
        return new ResolvedUserAccess(userId, "operator", tenantId, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), List.of("asset:claim"), List.of("ALL"), 1L);
    }

    private static long insertAsset(
            TransactionTemplate tx, AssetLedgerMapper mapper, long tenantId, long departmentId, String code) {
        AssetLedger asset = new AssetLedger();
        asset.setTenantId(tenantId);
        asset.setAssetCode(code);
        asset.setName("Test laptop");
        asset.setCategory("IT");
        asset.setStatus("IDLE");
        asset.setDepartmentId(departmentId);
        asset.setOriginalValue(BigDecimal.ZERO);
        asset.setVersion(0);
        asset.setDeleted(false);
        tx.executeWithoutResult(status -> mapper.insert(asset));
        return asset.getId();
    }

    private static AdminAssetsServiceImpl service(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new AdminAssetsServiceImpl(
                session.getMapper(AssetLedgerMapper.class), session.getMapper(AssetOperationMapper.class),
                session.getMapper(AccessControlMapper.class), mock(MeetingRoomMapper.class),
                mock(MeetingBookingMapper.class), mock(VisitorBookingMapper.class), mock(SealUsageMapper.class),
                session.getMapper(UserMapper.class), mock(WorkflowInstanceMapper.class),
                mock(WorkflowTaskMapper.class), mock(WorkflowActionLogMapper.class), access, audit,
                mock(NotificationService.class));
    }
}
