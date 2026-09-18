package com.aiworkmate.integration;

import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.mapper.*;
import com.aiworkmate.agent.task.AgentTaskMapper;
import com.aiworkmate.service.*;
import com.aiworkmate.service.impl.*;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real domain calls inside the isolated PostgreSQL migration gate. */
final class LeaveWithdrawalPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var ds = new DriverManagerDataSource(url, username, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema);
        ds.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(ds);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long user = jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id) VALUES ('withdraw-verifier','test-only','withdraw@example.invalid',?) RETURNING id", Long.class, tenant);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper : List.of(LeaveApplicationMapper.class, WorkflowInstanceMapper.class,
                WorkflowTaskMapper.class, WorkflowActionLogMapper.class, UserMapper.class,
                AgentTaskMapper.class, BusinessAuditLogMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds);
        factory.setConfiguration(config);
        factory.setMapperLocations(new ClassPathResource("mapper/LeaveApplicationMapper.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user,"test",tenant,
                "EMPLOYEE",List.of("EMPLOYEE"),List.of("leave:withdraw"),List.of("SELF"),1L));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        Long id = fixture(jdbc, tenant, user);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> call = () -> { barrier.await(10, TimeUnit.SECONDS);
                try { tx.execute(status -> service.withdraw(user,id,new VersionRequest(0))); return true; }
                catch (com.aiworkmate.common.BusinessException error) {
                    assertThat(error.getErrorCode()).isIn("VERSION_CONFLICT","BUSINESS_STATE_INVALID"); return false;
                } };
            var first=executor.submit(call); var second=executor.submit(call);
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        } finally { executor.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT status FROM leave_application WHERE id=?",String.class,id)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_task WHERE business_type='LEAVE_APPLICATION' AND business_id=?",String.class,id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='LEAVE_APPLICATION' AND resource_id=? AND action='WITHDRAW'",Integer.class,id.toString())).isOne();
        Long rollbackId=fixture(jdbc,tenant,user);
        var failing=mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failing)
                .recordTransactional(any(),any(),any(),any(),any(),any(),any());
        var failingService=service(session,access,failing);
        assertThatThrownBy(() -> tx.execute(status -> failingService.withdraw(user,rollbackId,new VersionRequest(0))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM leave_application WHERE id=?",String.class,rollbackId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_task WHERE business_type='LEAVE_APPLICATION' AND business_id=?",String.class,rollbackId)).isEqualTo("PENDING");
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user,"test",tenant,
                "EMPLOYEE",List.of("EMPLOYEE"),List.of(),List.of("SELF"),2L));
        assertThatThrownBy(() -> tx.execute(status -> service.withdraw(user,rollbackId,new VersionRequest(0))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user,"test",tenant+100,
                "EMPLOYEE",List.of("EMPLOYEE"),List.of("leave:withdraw"),List.of("SELF"),3L));
        assertThatThrownBy(() -> tx.execute(status -> service.withdraw(user,rollbackId,new VersionRequest(0))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        Long otherUser=jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id) VALUES ('withdraw-other','test-only','withdraw-other@example.invalid',?) RETURNING id",Long.class,tenant);
        when(access.resolveActiveUser(otherUser)).thenReturn(new ResolvedUserAccess(otherUser,"test",tenant,
                "EMPLOYEE",List.of("EMPLOYEE"),List.of("leave:withdraw"),List.of("SELF"),1L));
        assertThatThrownBy(() -> tx.execute(status -> service.withdraw(otherUser,rollbackId,new VersionRequest(0))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM leave_application WHERE id=?",String.class,rollbackId)).isEqualTo("PENDING");
    }

    private static LeaveWorkflowServiceImpl service(SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new LeaveWorkflowServiceImpl(session.getMapper(LeaveApplicationMapper.class),
                session.getMapper(WorkflowInstanceMapper.class),session.getMapper(WorkflowTaskMapper.class),
                session.getMapper(WorkflowActionLogMapper.class),session.getMapper(AgentTaskMapper.class),
                session.getMapper(UserMapper.class),access,audit,mock(NotificationService.class));
    }

    private static Long fixture(JdbcTemplate jdbc,Long tenant,Long user) {
        Long definition=jdbc.queryForObject("SELECT id FROM workflow_definition WHERE tenant_id=? LIMIT 1",Long.class,tenant);
        Long id=jdbc.queryForObject("INSERT INTO leave_application(tenant_id,applicant_user_id,leave_type,start_date,start_period,end_date,end_period,duration_half_days,reason,status) VALUES (?,?,'PERSONAL',CURRENT_DATE,'AM',CURRENT_DATE,'PM',2,'test','PENDING') RETURNING id",Long.class,tenant,user);
        Long instance=jdbc.queryForObject("INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status) VALUES (?,?,'LEAVE_APPLICATION',?,?,'RUNNING') RETURNING id",Long.class,tenant,definition,id,user);
        jdbc.update("UPDATE leave_application SET workflow_instance_id=? WHERE id=?",instance,id);
        jdbc.update("INSERT INTO workflow_task(tenant_id,instance_id,business_type,business_id,assignee_user_id,status) VALUES (?,?,'LEAVE_APPLICATION',?,?,'PENDING')",tenant,instance,id,user);
        return id;
    }
}
