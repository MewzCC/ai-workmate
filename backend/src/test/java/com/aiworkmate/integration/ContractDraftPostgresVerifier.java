package com.aiworkmate.integration;

import com.aiworkmate.dto.ContractRequest;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.mapper.BusinessContractMapper;
import com.aiworkmate.mapper.ContractEventMapper;
import com.aiworkmate.mapper.SupplierMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.impl.ContractServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real PostgreSQL proof for a tenant-scoped contract draft and atomic audit. */
final class ContractDraftPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long owner = jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,display_name,tenant_id,status,employment_status)
                VALUES ('contract-agent-owner','test-only','contract-owner@example.invalid','合同负责人',?,1,'ACTIVE')
                RETURNING id
                """, Long.class, tenant);

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(BusinessContractMapper.class, ContractEventMapper.class,
                SupplierMapper.class, UserMapper.class, BusinessAuditLogMapper.class)) {
            configuration.addMapper(mapper);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(owner)).thenReturn(new ResolvedUserAccess(
                owner, "contract-agent-owner", tenant, "FINANCE_ADMIN", List.of("FINANCE_ADMIN"),
                List.of("route:contracts", "contract:manage"), List.of("TENANT"), 1L));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));

        var created = tx.execute(status -> service.create(owner, request("AGENT-CONTRACT-1", owner)));
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.fulfillmentStatus()).isEqualTo("NOT_STARTED");
        assertThat(created.version()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_contract
                WHERE tenant_id=? AND contract_code='AGENT-CONTRACT-1'
                  AND status='DRAFT' AND fulfillment_status='NOT_STARTED'
                  AND paid_amount=0 AND version=0
                """, Integer.class, tenant)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM contract_event
                WHERE tenant_id=? AND contract_id=? AND event_type='CREATED'
                  AND to_value='DRAFT'
                """, Integer.class, tenant, created.id())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE tenant_id=? AND resource_type='CONTRACT' AND resource_id=? AND action='CREATE'
                """, Integer.class, tenant, created.id().toString())).isOne();

        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failing = service(session, access, failingAudit);
        assertThatThrownBy(() -> tx.execute(status ->
                failing.create(owner, request("AGENT-CONTRACT-ROLLBACK", owner))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_contract
                WHERE tenant_id=? AND contract_code='AGENT-CONTRACT-ROLLBACK'
                """, Integer.class, tenant)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM contract_event event
                JOIN business_contract contract ON contract.id=event.contract_id
                WHERE event.tenant_id=? AND contract.contract_code='AGENT-CONTRACT-ROLLBACK'
                """, Integer.class, tenant)).isZero();
    }

    private static ContractServiceImpl service(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new ContractServiceImpl(
                session.getMapper(BusinessContractMapper.class), session.getMapper(ContractEventMapper.class),
                session.getMapper(SupplierMapper.class), session.getMapper(UserMapper.class), access, audit,
                mock(NotificationService.class), mock(MessageSource.class));
    }

    private static ContractRequest request(String code, Long owner) {
        return new ContractRequest(code, "Agent 合同草稿", "PURCHASE", "示例公司", null, owner,
                new BigDecimal("100000.00"), "CNY", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30), "受控创建", null);
    }
}
