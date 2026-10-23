package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.*;
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
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real migrated tables and production MyBatis reads; identity resolution is a deliberate test boundary. */
final class ApprovalFormDefinitionPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema); datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource); var mapper = new ObjectMapper();
        var row = jdbc.queryForMap("SELECT * FROM agent_tool WHERE tenant_id IS NULL AND code='approval.form.definition.query'");
        ToolDefinition definition;
        try (var definitions = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            definitions.registerBean(ObjectMapper.class, () -> mapper);
            definitions.register(AgentReadToolDefinitions.class);
            definitions.refresh();
            definition = definitions.getBean("approvalFormDefinitionQueryToolDefinition", ToolDefinition.class);
        }
        // jsonb reorders object keys: compare schema trees, never re-hash its serialization.
        assertThat(mapper.readTree(row.get("parameters_schema").toString())).isEqualTo(definition.inputSchema());
        assertThat(mapper.readTree(row.get("output_schema").toString())).isEqualTo(definition.outputSchema());
        assertThat(row.get("schema_hash")).isEqualTo(definition.schemaHash()).isEqualTo("sha256:b91991992231bd332647b3c0942cba36cce1372e9b5d67692b2090366727e00f");
        assertThat(row.get("side_effect")).isEqualTo("NONE"); assertThat(row.get("risk_level")).isEqualTo("L0");
        assertThat(row.get("enabled")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rbac_role_permission p WHERE p.permission_code='agent:tool:approval.form.definition.query' AND NOT EXISTS (SELECT 1 FROM rbac_role_permission r WHERE r.tenant_id=p.tenant_id AND r.role_code=p.role_code AND r.permission_code='approval:read')", Long.class)).isZero();
        long tenant = tenant(jdbc, "FORM_READ_A"); long foreign = tenant(jdbc, "FORM_READ_B");
        String json = "{\"fields\":[{\"name\":\"reason\",\"label\":\"Reason\",\"type\":\"text\",\"required\":true,\"options\":[],\"width\":\"full\"}]}";
        long own = form(jdbc, tenant, json); long other = form(jdbc, foreign, json);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(ApprovalFormMapper.class, ApprovalProcessMapper.class, ApprovalRuleMapper.class, UserMapper.class)) configuration.addMapper(type);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(datasource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject()); var access = mock(UserAccessService.class); var audit = mock(BusinessAuditService.class);
        when(access.resolveActiveUser(7L)).thenReturn(reader(tenant));
        var service = new ApprovalEngineServiceImpl(session.getMapper(ApprovalFormMapper.class), session.getMapper(ApprovalProcessMapper.class),
                session.getMapper(ApprovalRuleMapper.class), session.getMapper(UserMapper.class), access, audit, mapper);
        long defaultTenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        when(access.resolveActiveUser(7L)).thenReturn(reader(defaultTenant));
        var seeded = jdbc.queryForList("SELECT id FROM approval_form WHERE tenant_id=? AND deleted=FALSE", Long.class, defaultTenant);
        assertThat(seeded).hasSizeGreaterThanOrEqualTo(6);
        for (Long id : seeded) assertThat(service.readFormDefinitionAgent(7L, defaultTenant, id).fields()).isNotEmpty();
        long expense = jdbc.queryForObject("SELECT id FROM approval_form WHERE tenant_id=? AND form_key='expense-application'", Long.class, defaultTenant);
        assertThat(service.readFormDefinitionAgent(7L, defaultTenant, expense).fields().get(0).min()).isEqualByComparingTo("0.01");
        when(access.resolveActiveUser(7L)).thenReturn(reader(tenant));
        var result = service.readFormDefinitionAgent(7L, tenant, own);
        assertThat(result.formId()).isEqualTo(own); assertThat(result.version()).isEqualTo(3);
        assertThat(result.fields()).singleElement().satisfies(field -> assertThat(field.name()).isEqualTo("reason"));
        assertThat(mapper.valueToTree(result).toString()).doesNotContain("schemaJson", "tenantId", "createdBy");
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, tenant, other)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE approval_form SET deleted=TRUE WHERE id=?", own);
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, tenant, own)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        when(access.resolveActiveUser(7L)).thenReturn(reader(foreign));
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, tenant, other)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        assertThat(jdbc.queryForObject("SELECT version FROM approval_form WHERE id=?", Integer.class, own)).isEqualTo(3);
        verifyNoInteractions(audit);
    }
    private static ResolvedUserAccess reader(long tenant) {
        return new ResolvedUserAccess(7L, "fixture", tenant, "EMPLOYEE", List.of("EMPLOYEE"), List.of("approval:read"), List.of("TENANT"), 1L);
    }
    private static long tenant(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Form read fixture') RETURNING id", Long.class, code);
    }
    private static long form(JdbcTemplate jdbc, long tenant, String schema) {
        return jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,version) VALUES (?,'definition_fixture','Definition fixture',?,'DISABLED',3) RETURNING id", Long.class, tenant, schema);
    }
}
