package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.ApprovalFormMapper;
import com.aiworkmate.mapper.ApprovalProcessMapper;
import com.aiworkmate.mapper.ApprovalRuleMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.ApprovalEngineServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.Properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Actual migrated-table reads with deliberately inconsistent cross-tenant legacy references. */
final class ApprovalProcessReferencePostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema); datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        long tenant = tenant(jdbc, "PROCESS_REF_A"); long foreignTenant = tenant(jdbc, "PROCESS_REF_B");
        long own = form(jdbc, tenant, "Own form"); long foreign = form(jdbc, foreignTenant, "Foreign private form");
        long process = jdbc.queryForObject("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status,version) VALUES (?,'reference_fixture','Reference fixture',?,'[]','DISABLED',3) RETURNING id", Long.class, tenant, foreign);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(ApprovalFormMapper.class, ApprovalProcessMapper.class, ApprovalRuleMapper.class, UserMapper.class)) configuration.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(datasource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject()); var access = mock(UserAccessService.class); var audit = mock(BusinessAuditService.class);
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", tenant, "EMPLOYEE", List.of("EMPLOYEE"), List.of("approval:read"), List.of("TENANT"), 1L));
        var service = new ApprovalEngineServiceImpl(session.getMapper(ApprovalFormMapper.class), session.getMapper(ApprovalProcessMapper.class),
                session.getMapper(ApprovalRuleMapper.class), session.getMapper(UserMapper.class), access, audit, new ObjectMapper());
        assertThat(service.getProcess(7L, process).formName()).isNull();
        assertThat(service.listProcesses(7L, "reference_fixture", null, 1, 20).records()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(process); assertThat(row.formName()).isNull(); assertThat(row.canEdit()).isFalse();
        });
        jdbc.update("UPDATE approval_process SET form_id=? WHERE id=?", own, process);
        assertThat(service.getProcess(7L, process).formName()).isEqualTo("Own form");
        assertThat(service.listProcesses(7L, null, null, 1, 20).records()).singleElement().satisfies(row -> assertThat(row.formName()).isEqualTo("Own form"));
        jdbc.update("UPDATE approval_form SET deleted=TRUE WHERE id=?", own);
        assertThat(service.getProcess(7L, process).formName()).isNull();
        assertThat(service.listProcesses(7L, null, null, 1, 20).records()).singleElement().satisfies(row -> assertThat(row.formName()).isNull());
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", foreignTenant, "EMPLOYEE", List.of("EMPLOYEE"), List.of("approval:read"), List.of("TENANT"), 1L));
        assertThatThrownBy(() -> service.getProcess(7L, process)).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT version FROM approval_process WHERE id=?", Integer.class, process)).isEqualTo(3);
        verifyNoInteractions(audit);
    }
    private static long tenant(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Process reference fixture') RETURNING id", Long.class, code);
    }
    private static long form(JdbcTemplate jdbc, long tenant, String name) {
        return jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status) VALUES (?,'reference_fixture',?,'{}','DISABLED') RETURNING id", Long.class, tenant, name);
    }
}
