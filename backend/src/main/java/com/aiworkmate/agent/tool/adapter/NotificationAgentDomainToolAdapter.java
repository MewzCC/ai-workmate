package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationAgentDomainToolAdapter implements NotificationToolPort {
    private final NotificationService notificationService;

    @Override
    public NotificationToolPort.Page mine(ToolActorContext context, int page, int size) {
        var result = notificationService.list(context.userId(), page, size);
        return new NotificationToolPort.Page(result.records().stream().map(item -> new NotificationToolPort.Item(
                item.id(), item.type(), item.title(), item.content(), item.bizType(), item.read(), item.createdAt()))
                .toList(), result.total(), result.page(), result.size());
    }

    @Override
    public NotificationToolPort.ReadResult markRead(ToolActorContext context, long notificationId) {
        notificationService.markRead(context.userId(), notificationId);
        return new NotificationToolPort.ReadResult(notificationId, true);
    }
}
