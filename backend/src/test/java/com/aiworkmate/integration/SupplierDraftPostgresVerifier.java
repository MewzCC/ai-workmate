package com.aiworkmate.integration;

import com.aiworkmate.dto.SupplierRequest;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.mapper.SupplierMapper;
import com.aiworkmate.mapper.SupplierStatusHistoryMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.impl.SupplierServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

final class SupplierDraftPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long user = jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,display_name,tenant_id,status,employment_status)
                VALUES ('supplier-agent-owner','test-only','supplier-owner@example.invalid','供应商管理员',?,1,'ACTIVE')
                RETURNING id
                """, Long.class, tenant);

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SupplierMapper.class);
        configuration.addMapper(SupplierStatusHistoryMapper.class);
        configuration.addMapper(BusinessAuditLogMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(
                user, "supplier-agent-owner", tenant, "FINANCE_ADMIN", List.of("FINANCE_ADMIN"),
                List.of("route:suppliers", "supplier:manage"), List.of("TENANT"), 1L));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        var service = service(session, access, audit);

        var created = tx.execute(status -> service.create(user, request("AGENT-SUP-1")));
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.version()).isZero();
        assertThat(created.unifiedSocialCreditCode()).isNull();
        assertThat(created.contactPhone()).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM supplier WHERE tenant_id=? AND supplier_code='AGENT-SUP-1'
                  AND status='DRAFT' AND version=0 AND unified_social_credit_code IS NULL
                  AND contact_name IS NULL AND contact_phone IS NULL AND contact_email IS NULL
                  AND address IS NULL AND risk_note IS NULL
                """, Integer.class, tenant)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM supplier_status_history
                WHERE tenant_id=? AND supplier_id=? AND from_status IS NULL AND to_status='DRAFT'
                """, Integer.class, tenant, created.id())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE tenant_id=? AND resource_type='SUPPLIER' AND resource_id=? AND action='CREATE'
                """, Integer.class, tenant, created.id().toString())).isOne();

        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failing = service(session, access, failingAudit);
        assertThatThrownBy(() -> tx.execute(status -> failing.create(user, request("AGENT-SUP-ROLLBACK"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM supplier WHERE tenant_id=? AND supplier_code='AGENT-SUP-ROLLBACK'",
                Integer.class, tenant)).isZero();
    }

    private static SupplierServiceImpl service(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new SupplierServiceImpl(session.getMapper(SupplierMapper.class),
                session.getMapper(SupplierStatusHistoryMapper.class), access, audit);
    }

    private static SupplierRequest request(String code) {
        return new SupplierRequest(code, "Agent 供应商草稿", "Agent供应商", null,
                "SERVICE", "STANDARD", null, null, null, null, "月结30天", null, null);
    }
}
