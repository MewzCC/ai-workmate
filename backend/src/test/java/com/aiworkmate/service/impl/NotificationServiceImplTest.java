package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.entity.Notification;
import com.aiworkmate.mapper.NotificationMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOps;
    @Mock private UserAccessService userAccessService;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Notification.class);
        notificationService = new NotificationServiceImpl(
                notificationMapper, redisTemplate, new ObjectMapper(), userAccessService);
        org.mockito.Mockito.lenient().when(userAccessService.resolveActiveUser(2L)).thenReturn(
                new ResolvedUserAccess(2L, "user", 1L, "EMPLOYEE", java.util.List.of("EMPLOYEE"),
                        java.util.List.of("notification:read:self"), java.util.List.of("SELF"), 1L));
    }

    @Test
    void publishShouldEnqueueToRedisQueue() {
        when(redisTemplate.opsForList()).thenReturn(listOps);

        notificationService.publish(1L, 2L, "approval", "新的请假申请待审批",
                "员工提交了请假申请，请及时处理", "leave", 42L);

        verify(listOps).leftPush(eq("notification:queue"), contains("\"userId\":2"));
        verifyNoInteractions(notificationMapper);
    }

    @Test
    void publishShouldFallBackToDirectInsertWhenRedisUnavailable() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.leftPush(anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        notificationService.publish(1L, 2L, "approval", "标题", "内容", "leave", 42L);

        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    void consumeOneShouldInsertNotificationFromQueue() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.rightPop(eq("notification:queue"), any(Duration.class)))
                .thenReturn("{\"tenantId\":1,\"userId\":2,\"type\":\"approval\",\"title\":\"标题\",\"content\":\"内容\",\"bizType\":\"leave\",\"bizId\":42}");

        boolean consumed = notificationService.consumeOne();

        assertThat(consumed).isTrue();
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    void consumeOneShouldReturnFalseWhenQueueEmpty() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.rightPop(eq("notification:queue"), any(Duration.class))).thenReturn(null);

        boolean consumed = notificationService.consumeOne();

        assertThat(consumed).isFalse();
        verifyNoInteractions(notificationMapper);
    }

    @Test
    void listShouldReturnOnlyCurrentUserNotifications() {
        when(notificationMapper.selectCount(any())).thenReturn(1L);
        Notification notification = new Notification();
        notification.setId(9L);
        notification.setType("approval");
        notification.setTitle("标题");
        notification.setContent("内容");
        notification.setReadFlag(false);
        when(notificationMapper.selectList(any())).thenReturn(java.util.List.of(notification));

        var result = notificationService.list(2L, 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records().get(0).id()).isEqualTo(9L);
        assertThat(result.records().get(0).read()).isFalse();
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>> captor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(notificationMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("tenant_id", "user_id");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValues(1L, 2L);
    }

    @Test
    void listShouldFailClosedWhenNotificationPermissionWasRevoked() {
        when(userAccessService.resolveActiveUser(3L)).thenReturn(new ResolvedUserAccess(
                3L, "other", 1L, "EMPLOYEE", java.util.List.of("EMPLOYEE"),
                java.util.List.of(), java.util.List.of("SELF"), 1L));

        assertThatThrownBy(() -> notificationService.list(3L, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("PERMISSION_DENIED"));
        verify(notificationMapper, never()).selectList(any());
    }

    @Test
    void markReadShouldRejectNotificationOwnedByAnotherUser() {
        when(notificationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> notificationService.markRead(2L, 99L))
                .isInstanceOf(BusinessException.class);
        verify(notificationMapper, never()).update(any(), any());
    }

    @Test
    void markAllReadShouldReturnUpdatedCount() {
        when(notificationMapper.update(any(), any())).thenReturn(3);

        long updated = notificationService.markAllRead(2L);

        assertThat(updated).isEqualTo(3);
    }
}
