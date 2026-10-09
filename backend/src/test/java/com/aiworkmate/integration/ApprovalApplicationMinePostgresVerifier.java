package com.aiworkmate.integration;

import com.aiworkmate.agent.tool.adapter.ApprovalApplicationAgentDomainToolAdapter;
import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.*;
import com.aiworkmate.service.impl.GenericApprovalServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests the real generic-application mapper/domain/adapter only in isolated migration schemas. */
final class ApprovalApplicationMinePostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES ('APP_MINE_A','Test A') RETURNING id", Long.class);
        Long otherTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES ('APP_MINE_B','Test B') RETURNING id", Long.class);
        Long user = user(jdbc, tenant, "app-mine-a");
        Long colleague = user(jdbc, tenant, "app-mine-colleague");
        Long foreign = user(jdbc, otherTenant, "app-mine-foreign");
        Long form = form(jdbc, tenant);
        Long foreignForm = form(jdbc, otherTenant);
        for (String status : List.of("DRAFT", "PENDING", "APPROVED")) insert(jdbc, tenant, user, form, status);
        insert(jdbc, tenant, colleague, form, "DRAFT");
        insert(jdbc, otherTenant, foreign, foreignForm, "DRAFT");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ApprovalApplicationMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/ApprovalApplicationMapper.xml"));
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ApprovalApplicationMapper.class);
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of("route:approval-start", "approval:mine:read"), List.of("SELF"), 1L));
        var domain = new GenericApprovalServiceImpl(mapper, mock(ApprovalFormMapper.class),
                mock(ApprovalProcessMapper.class), mock(ApprovalRuleMapper.class), mock(LeaveApplicationMapper.class),
                mock(UserMapper.class), mock(WorkflowInstanceMapper.class), mock(WorkflowTaskMapper.class),
                mock(WorkflowActionLogMapper.class), access, mock(BusinessAuditService.class),
                mock(NotificationService.class), new ObjectMapper());
        var adapter = new ApprovalApplicationAgentDomainToolAdapter(domain);
        var actor = new ToolActorContext(tenant, user, 20L, 30L, 1, "trace");
        var result = adapter.mine(actor, new ApprovalApplicationToolPort.Query(null, null, 1, 2));
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items()).hasSize(2);
        assertThat(result.toString()).doesNotContain("PRIVATE", "rawValue", "app-mine-colleague", "app-mine-foreign");
        var drafts = adapter.mine(actor, new ApprovalApplicationToolPort.Query("DRAFT", "test-form", 1, 20));
        assertThat(drafts.total()).isEqualTo(1);
        assertThat(drafts.items().get(0).canEditDraft()).isTrue();
        assertThat(adapter.mine(actor, new ApprovalApplicationToolPort.Query(null, "missing", 1, 20)).total()).isZero();
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of("route:approval-start"), List.of("SELF"), 2L));
        assertThatThrownBy(() -> adapter.mine(actor, new ApprovalApplicationToolPort.Query(null, null, 1, 20)))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }
    private static Long user(JdbcTemplate jdbc, Long tenant, String name) {
        return jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id) VALUES (?,'test-only',?,?) RETURNING id",
                Long.class, name, name + "@example.invalid", tenant);
    }
    private static Long form(JdbcTemplate jdbc, Long tenant) {
        return jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name) VALUES (?,'test-form','Test form') RETURNING id",
                Long.class, tenant);
    }
    private static void insert(JdbcTemplate jdbc, Long tenant, Long user, Long form, String status) {
        jdbc.update("INSERT INTO approval_application(tenant_id,applicant_user_id,form_id,form_key,form_name,title,data_json,status) VALUES (?,?,?,'test-form','Test form','PRIVATE TITLE','{\"rawValue\":\"PRIVATE\"}',?)",
                tenant, user, form, status);
    }
}
