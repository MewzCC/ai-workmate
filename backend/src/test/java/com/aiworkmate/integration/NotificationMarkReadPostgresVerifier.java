package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.entity.Notification;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.mapper.NotificationMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.impl.NotificationServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the single-notification write against the isolated migrated PostgreSQL schema. */
final class NotificationMarkReadPostgresVerifier {
    private NotificationMarkReadPostgresVerifier() { }

    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenantId = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long ownerId = insertUser(jdbc, tenantId, "notification-owner");
        Long otherId = insertUser(jdbc, tenantId, "notification-other");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NotificationMapper.class);
        configuration.addMapper(BusinessAuditLogMapper.class);
        var factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(datasource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();
        var session = new SqlSessionTemplate(factory);
        NotificationMapper notifications = session.getMapper(NotificationMapper.class);
        BusinessAuditService audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        UserAccessService access = mock(UserAccessService.class);
        when(access.resolveActiveUser(ownerId)).thenReturn(actor(ownerId, tenantId));
        when(access.resolveActiveUser(otherId)).thenReturn(actor(otherId, tenantId));
        var service = new NotificationServiceImpl(notifications, mock(StringRedisTemplate.class),
                new ObjectMapper(), access, audit);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(datasource));

        Long notificationId = transaction.execute(status -> insertNotification(notifications, tenantId, ownerId));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            var first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                transaction.executeWithoutResult(status -> service.markRead(ownerId, notificationId));
                return true;
            });
            var second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                transaction.executeWithoutResult(status -> service.markRead(ownerId, notificationId));
                return true;
            });
            assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT read_flag FROM notification WHERE id=?", Boolean.class,
                notificationId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log "
                + "WHERE resource_type='NOTIFICATION' AND resource_id=? AND action='MARK_READ'",
                Integer.class, notificationId.toString())).isOne();

        Long protectedId = transaction.execute(status -> insertNotification(notifications, tenantId, ownerId));
        assertThatThrownBy(() -> transaction.executeWithoutResult(
                status -> service.markRead(otherId, protectedId)))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT read_flag FROM notification WHERE id=?", Boolean.class,
                protectedId)).isFalse();

        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingService = new NotificationServiceImpl(notifications, mock(StringRedisTemplate.class),
                new ObjectMapper(), access, failingAudit);
        Long rollbackId = transaction.execute(status -> insertNotification(notifications, tenantId, ownerId));
        assertThatThrownBy(() -> transaction.executeWithoutResult(
                status -> failingService.markRead(ownerId, rollbackId)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT read_flag FROM notification WHERE id=?", Boolean.class,
                rollbackId)).isFalse();
    }

    private static Long insertUser(JdbcTemplate jdbc, Long tenantId, String username) {
        return jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id) "
                        + "VALUES (?, 'test-only', ? || '@example.invalid', ?) RETURNING id",
                Long.class, username, username, tenantId);
    }

    private static Long insertNotification(NotificationMapper mapper, Long tenantId, Long userId) {
        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setType("approval");
        notification.setTitle("Test notification");
        notification.setContent("Test content");
        notification.setReadFlag(false);
        notification.setCreatedAt(LocalDateTime.now());
        mapper.insert(notification);
        return notification.getId();
    }

    private static ResolvedUserAccess actor(Long userId, Long tenantId) {
        return new ResolvedUserAccess(userId, "test", tenantId, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("notification:read:self"), List.of("SELF"), 1L);
    }
}
