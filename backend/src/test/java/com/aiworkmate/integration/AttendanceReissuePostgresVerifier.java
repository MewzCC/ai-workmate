package com.aiworkmate.integration;

import com.aiworkmate.dto.AttendanceReissueRequest;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.AttendanceServiceImpl;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses only the isolated migrated schema owned by the database gate. */
final class AttendanceReissuePostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long approver = jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id) "
                + "VALUES ('reissue-approver','test-only','reissue-approver@example.invalid',?) RETURNING id",
                Long.class, tenant);
        Long user = jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id,approver_user_id) "
                + "VALUES ('reissue-applicant','test-only','reissue-applicant@example.invalid',?,?) RETURNING id",
                Long.class, tenant, approver);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(UserMapper.class, AttendanceRecordMapper.class, AttendanceReissueMapper.class,
                AttendanceSettingMapper.class, BusinessAuditLogMapper.class)) configuration.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of("attendance:reissue:apply"), List.of("SELF"), 1L));
        when(access.resolveActiveUser(approver)).thenReturn(new ResolvedUserAccess(approver, "test", tenant, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of(), List.of("SELF"), 1L));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        var request = new AttendanceReissueRequest(LocalDate.now().minusDays(1), "CLOCK_IN", "test reason");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> submit = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { tx.execute(status -> service.submitReissue(user, request)); return true; }
                catch (com.aiworkmate.common.BusinessException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo("BUSINESS_STATE_INVALID"); return false;
                }
            };
            var first = executor.submit(submit); var second = executor.submit(submit);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_reissue WHERE applicant_user_id=?",
                Integer.class, user)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='ATTENDANCE_REISSUE'",
                Integer.class)).isOne();
        var agentRequest = new AttendanceReissueRequest(
                LocalDate.now().minusDays(2), "CLOCK_OUT", "agent test reason");
        var agentFirst = tx.execute(status -> service.submitAgentReissue(user, agentRequest, "agent-operation-1"));
        var agentReplay = tx.execute(status -> service.submitAgentReissue(user, agentRequest, "agent-operation-1"));
        assertThat(agentFirst).isNotNull();
        assertThat(agentReplay).isNotNull();
        assertThat(agentReplay.id()).isEqualTo(agentFirst.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_reissue WHERE applicant_user_id=?",
                Integer.class, user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='ATTENDANCE_REISSUE'",
                Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> tx.execute(status -> service.submitAgentReissue(user,
                new AttendanceReissueRequest(agentRequest.clockDate(), agentRequest.clockType(), "changed"),
                "agent-operation-1"))).isInstanceOfSatisfying(
                        com.aiworkmate.common.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        var failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failing = service(session, access, failingAudit);
        var other = new AttendanceReissueRequest(request.clockDate(), "CLOCK_OUT", "test reason");
        assertThatThrownBy(() -> tx.execute(status -> failing.submitReissue(user, other)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_reissue WHERE applicant_user_id=?",
                Integer.class, user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_record WHERE user_id=?",
                Integer.class, user)).isZero();
    }

    private static AttendanceServiceImpl service(SqlSessionTemplate session, UserAccessService access,
                                                  BusinessAuditService audit) {
        return new AttendanceServiceImpl(session.getMapper(AttendanceRecordMapper.class),
                session.getMapper(AttendanceReissueMapper.class), session.getMapper(AttendanceSettingMapper.class),
                session.getMapper(UserMapper.class), access, audit);
    }
}
