package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.NotificationResponse;

/**
 * 站内通知服务。业务事件经 Redis 临时消息队列异步落库，
 * 提供列表、未读数、已读操作。
 */
public interface NotificationService {

    String TYPE_APPROVAL = "approval";
    String TYPE_SYSTEM = "system";
    String TYPE_ALERT = "alert";
    String TYPE_TODO = "todo";

    /** 生产：把通知事件推入 Redis 临时队列（异步落库）；Redis 不可用时降级为直接落库 */
    void publish(long tenantId, long userId, String type, String title, String content,
                 String bizType, Long bizId);

    /** 消费：从 Redis 队列取出一条并落库；队列为空返回 false */
    boolean consumeOne();

    /** 启动队列消费者守护线程（应用启动后调用） */
    void startConsumer();

    PageResponse<NotificationResponse> list(long userId, int page, int size);

    long unreadCount(long userId);

    void markRead(long userId, long notificationId);

    long markAllRead(long userId);
}
