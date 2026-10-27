package com.aiworkmate.integration;

import com.aiworkmate.agent.tool.adapter.ObservabilityAgentDomainToolAdapter;
import com.aiworkmate.agent.tool.port.ObservabilityToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.RuntimeLogMapper;
import com.aiworkmate.service.ObservabilityPreferenceService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.PlatformObservabilityServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real mapper/domain/adapter verification in the migration gate's isolated schema only. */
final class ObservabilityOverviewPostgresVerifier {
    private ObservabilityOverviewPostgresVerifier() { }

    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES ('OBS_TEST_A','Test A') RETURNING id", Long.class);
        Long otherTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES ('OBS_TEST_B','Test B') RETURNING id", Long.class);
        Long user = user(jdbc, tenant, "obs-test-a");
        Long otherUser = user(jdbc, otherTenant, "obs-test-b");
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                    INSERT INTO platform_operation_log(tenant_id,user_id,actor_label,event_type,http_method,request_path,
                    outcome,duration_ms,request_id,trace_id,error_code,started_at)
                    VALUES (?,?,'PRIVATE ACTOR','HTTP_READ','GET','/private/request',?,?,?,?,'TEST_ERROR',CURRENT_TIMESTAMP-INTERVAL '1 hour')
                    """, tenant, user, List.of("SUCCEEDED", "FAILED", "REJECTED").get(i), (i + 1) * 10,
                    "obs-a-" + i, "private-trace-" + i);
        }
        jdbc.update("""
                INSERT INTO platform_operation_log(tenant_id,user_id,actor_label,event_type,outcome,duration_ms,request_id,trace_id,started_at)
                VALUES (?,?,'PRIVATE OTHER','HTTP_READ','FAILED',100000,'obs-other','other-trace',CURRENT_TIMESTAMP-INTERVAL '1 hour'),
                       (?,?,'PRIVATE OLD','HTTP_READ','FAILED',100000,'obs-old','old-trace',CURRENT_TIMESTAMP-INTERVAL '40 days')
                """, otherTenant, otherUser, tenant, user);

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RuntimeLogMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(RuntimeLogMapper.class);
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), List.of("route:platform-observability", "runtime-log:read"), List.of("TENANT"), 1L));
        var adapter = new ObservabilityAgentDomainToolAdapter(mock(ObservabilityPreferenceService.class),
                new PlatformObservabilityServiceImpl(mapper, access));
        var actor = new ToolActorContext(tenant, user, 20L, 30L, 1, "trace");
        var result = adapter.overview(actor, new ObservabilityToolPort.OverviewQuery("30d"));
        assertThat(result.stats().total()).isEqualTo(3);
        assertThat(result.stats().failed()).isEqualTo(1);
        assertThat(result.stats().blocked()).isEqualTo(1);
        assertThat(result.stats().averageDurationMs()).isEqualTo(20);
        assertThat(result.p95DurationMs()).isEqualTo(29);
        assertThat(result.timeline().stream().mapToLong(ObservabilityToolPort.Timeline::total).sum()).isEqualTo(3);
        assertThat(result.sources()).containsExactly(new ObservabilityToolPort.Category("HUMAN", 3));
        assertThat(result.toString()).doesNotContain("PRIVATE", "private/request", "private-trace", "100000");
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), List.of("route:platform-observability"), List.of("TENANT"), 2L));
        assertThatThrownBy(() -> adapter.overview(actor, new ObservabilityToolPort.OverviewQuery("30d")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }

    private static Long user(JdbcTemplate jdbc, Long tenant, String username) {
        return jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id) VALUES (?,'test-only',?,?) RETURNING id",
                Long.class, username, username + "@example.invalid", tenant);
    }
}
