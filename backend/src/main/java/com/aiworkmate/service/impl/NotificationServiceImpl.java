package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.NotificationResponse;
import com.aiworkmate.entity.Notification;
import com.aiworkmate.mapper.NotificationMapper;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知实现：Redis List 作为临时消息队列（LPUSH 生产 / BRPOP 消费），
 * 消费者线程异步落库到 notification 表；Redis 不可用时降级为直接落库，保证通知不丢失。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String QUEUE_KEY = "notification:queue";
    private static final Duration POP_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration IDLE_SLEEP = Duration.ofMillis(200);
    private final NotificationMapper notificationMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserAccessService userAccessService;

    @PostConstruct
    public void init() {
        startConsumer();
    }

    @Override
    public void publish(long tenantId, long userId, String type, String title, String content,
                        String bizType, Long bizId) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "通知内容不能为空");
        }
        QueueMessage message = new QueueMessage(tenantId, userId, type, title, content, bizType, bizId);
        try {
            redisTemplate.opsForList().leftPush(QUEUE_KEY, toJson(message));
            log.debug("Notification enqueued, userId={}, type={}", userId, type);
        } catch (RuntimeException ex) {
            // Redis 不可用时降级为直接落库，通知不丢失
            log.warn("Redis queue unavailable, fallback to direct insert, userId={}, type={}", userId, type, ex);
            insertDirect(message);
        }
    }

    @Override
    public boolean consumeOne() {
        String raw;
        try {
            raw = redisTemplate.opsForList().rightPop(QUEUE_KEY, POP_TIMEOUT);
        } catch (RuntimeException ex) {
            log.warn("Redis queue consume failed, will retry", ex);
            return false;
        }
        if (raw == null) return false;
        try {
            insertDirect(fromJson(raw));
            return true;
        } catch (RuntimeException ex) {
            log.error("Notification consume failed, raw={}", raw, ex);
            return false;
        }
    }

    @Override
    public void startConsumer() {
        Thread worker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (!consumeOne()) {
                        Thread.sleep(IDLE_SLEEP.toMillis());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.info("Notification consumer stopped");
                    break;
                } catch (RuntimeException ex) {
                    log.error("Notification consumer error", ex);
                    try {
                        Thread.sleep(IDLE_SLEEP.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "notification-consumer");
        worker.setDaemon(true);
        worker.start();
        log.info("Notification consumer started");
    }

    @Override
    public PageResponse<NotificationResponse> list(long userId, int page, int size) {
        ResolvedUserAccess access = requireNotificationAccess(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        Long total = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getTenantId, access.tenantId())
                .eq(Notification::getUserId, access.userId()));
        List<Notification> rows = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getTenantId, access.tenantId())
                .eq(Notification::getUserId, access.userId())
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + ((long) (safePage - 1) * safeSize)));
        return PageResponse.of(rows.stream().map(this::toResponse).toList(),
                total == null ? 0 : total, safePage, safeSize);
    }

    @Override
    public long unreadCount(long userId) {
        ResolvedUserAccess access = requireNotificationAccess(userId);
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getTenantId, access.tenantId())
                .eq(Notification::getUserId, access.userId())
                .eq(Notification::getReadFlag, false));
        return count == null ? 0 : count;
    }

    @Override
    public void markRead(long userId, long notificationId) {
        ResolvedUserAccess access = requireNotificationAccess(userId);
        Notification notification = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getTenantId, access.tenantId())
                .eq(Notification::getUserId, access.userId()));
        if (notification == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getTenantId, access.tenantId())
                .eq(Notification::getUserId, access.userId())
                .eq(Notification::getReadFlag, false)
                .set(Notification::getReadFlag, true));
    }

    @Override
    public long markAllRead(long userId) {
        ResolvedUserAccess access = requireNotificationAccess(userId);
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getTenantId, access.tenantId())
                .eq(Notification::getUserId, access.userId())
                .eq(Notification::getReadFlag, false)
                .set(Notification::getReadFlag, true));
        return updated;
    }

    private void insertDirect(QueueMessage message) {
        Notification notification = new Notification();
        notification.setTenantId(message.tenantId());
        notification.setUserId(message.userId());
        notification.setType(message.type());
        notification.setTitle(message.title());
        notification.setContent(message.content());
        notification.setBizType(message.bizType());
        notification.setBizId(message.bizId());
        notification.setReadFlag(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getBizType(),
                notification.getBizId(),
                Boolean.TRUE.equals(notification.getReadFlag()),
                notification.getCreatedAt());
    }

    private ResolvedUserAccess requireNotificationAccess(long userId) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (access == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!access.permissions().contains("notification:read:self")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return access;
    }

    private String toJson(QueueMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "通知消息序列化失败");
        }
    }

    private QueueMessage fromJson(String raw) {
        try {
            return objectMapper.readValue(raw, QueueMessage.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "通知消息解析失败");
        }
    }

    private record QueueMessage(long tenantId, long userId, String type, String title,
                                String content, String bizType, Long bizId) {
    }
}
