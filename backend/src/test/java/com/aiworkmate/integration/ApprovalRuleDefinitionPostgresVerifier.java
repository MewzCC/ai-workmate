package com.aiworkmate.integration;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.agent.registry.AgentReadToolDefinitions;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.ApprovalEngineServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real tenant/id/deleted filtering and frozen read contract, isolated migrated schemas only. */
final class ApprovalRuleDefinitionPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema); datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource); var mapper = new ObjectMapper(); ToolDefinition definition;
        try (var beans = new AnnotationConfigApplicationContext()) {
            beans.registerBean(ObjectMapper.class, () -> mapper); beans.register(AgentReadToolDefinitions.class); beans.refresh();
            definition = beans.getBean("approvalRuleDefinitionQueryToolDefinition", ToolDefinition.class);
        }
        var registration = jdbc.queryForMap("SELECT * FROM agent_tool WHERE tenant_id IS NULL AND code='approval.rule.definition.query'");
        assertThat(registration.get("risk_level")).isEqualTo("L0"); assertThat(registration.get("side_effect")).isEqualTo("NONE");
        assertThat(registration.get("confirmation_policy")).isEqualTo("NONE"); assertThat(registration.get("retry_policy")).isEqualTo("READ_ONLY_SAFE");
        assertThat(registration.get("schema_hash")).isEqualTo(definition.schemaHash())
                .isEqualTo("sha256:a595eafd0035099b68fcf7f97867ea848d13cb214cd2bceda203936fd2031b23");
        assertThat(mapper.readTree(registration.get("parameters_schema").toString())).isEqualTo(definition.inputSchema());
        assertThat(mapper.readTree(registration.get("output_schema").toString())).isEqualTo(definition.outputSchema());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rbac_role_permission p WHERE p.permission_code='agent:tool:approval.rule.definition.query' AND NOT EXISTS (SELECT 1 FROM rbac_role_permission r WHERE r.tenant_id=p.tenant_id AND r.role_code=p.role_code AND r.permission_code='approval:read')", Long.class)).isZero();
        long ownTenant = tenant(jdbc, "RULE_DEFINITION_A"), foreignTenant = tenant(jdbc, "RULE_DEFINITION_B");
        long own = rule(jdbc, ownTenant), foreign = rule(jdbc, foreignTenant);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(ApprovalFormMapper.class, ApprovalProcessMapper.class, ApprovalRuleMapper.class, UserMapper.class)) configuration.addMapper(type);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(datasource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject()); var access = mock(UserAccessService.class); var audit = mock(BusinessAuditService.class);
        var service = new ApprovalEngineServiceImpl(session.getMapper(ApprovalFormMapper.class), session.getMapper(ApprovalProcessMapper.class),
                session.getMapper(ApprovalRuleMapper.class), session.getMapper(UserMapper.class), access, audit, mapper);
        long defaultTenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        when(access.resolveActiveUser(7L)).thenReturn(reader(defaultTenant, List.of("approval:read")));
        var seeded = jdbc.queryForList("SELECT id FROM approval_rule WHERE tenant_id=? AND deleted=FALSE", Long.class, defaultTenant);
        assertThat(seeded).hasSizeGreaterThanOrEqualTo(2);
        for (Long id : seeded) assertThat(service.readRuleDefinitionAgent(7L, defaultTenant, id).conditions()).isNotEmpty();
        when(access.resolveActiveUser(7L)).thenReturn(reader(ownTenant, List.of("approval:read")));
        var result = service.readRuleDefinitionAgent(7L, ownTenant, own);
        assertThat(result.ruleId()).isEqualTo(own); assertThat(result.version()).isEqualTo(3);
        assertThat(result.conditions().get(0).value()).isEqualTo("0.123456789012345678901");
        var receipt = mapper.valueToTree(result); assertThat(new ToolSchemaValidator().valid(definition.outputSchema(), receipt)).isTrue();
        jdbc.update("UPDATE approval_rule SET priority=2147483647 WHERE id=?", own);
        var highPriority = service.readRuleDefinitionAgent(7L, ownTenant, own);
        assertThat(highPriority.priority()).isEqualTo(Integer.MAX_VALUE);
        assertThat(new ToolSchemaValidator().valid(definition.outputSchema(), mapper.valueToTree(highPriority))).isTrue();
        assertThat(receipt.toString()).doesNotContain("conditionJson", "actionJson", "description", "createdBy", "tenantId");
        assertThatThrownBy(() -> service.readRuleDefinitionAgent(7L, ownTenant, foreign)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE approval_rule SET deleted=TRUE WHERE id=?", own);
        assertThatThrownBy(() -> service.readRuleDefinitionAgent(7L, ownTenant, own)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        when(access.resolveActiveUser(7L)).thenReturn(reader(foreignTenant, List.of("approval:read")));
        assertThatThrownBy(() -> service.readRuleDefinitionAgent(7L, ownTenant, foreign)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        when(access.resolveActiveUser(7L)).thenReturn(reader(foreignTenant, List.of()));
        assertThatThrownBy(() -> service.readRuleDefinitionAgent(7L, foreignTenant, foreign)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM approval_rule WHERE id=?", Integer.class, own)).isEqualTo(3);
        verifyNoInteractions(audit);
    }
    private static ResolvedUserAccess reader(long tenant, List<String> permissions) {
        return new ResolvedUserAccess(7L, "fixture", tenant, "EMPLOYEE", List.of("EMPLOYEE"), permissions, List.of("TENANT"), 1L);
    }
    private static long tenant(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Rule read fixture') RETURNING id", Long.class, code);
    }
    private static long rule(JdbcTemplate jdbc, long tenant) {
        return jdbc.queryForObject("INSERT INTO approval_rule(tenant_id,rule_key,rule_name,rule_type,priority,condition_json,action_json,status,version) VALUES (?,'rule_read_fixture','Rule fixture','AMOUNT_THRESHOLD',20,?,?,'DISABLED',3) RETURNING id", Long.class,
                tenant, "{\"field\":\"amount\",\"op\":\"gte\",\"value\":0.123456789012345678901}", "{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":true}");
    }
}
