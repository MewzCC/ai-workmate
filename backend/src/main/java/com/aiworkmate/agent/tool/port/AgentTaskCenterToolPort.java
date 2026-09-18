package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary for a future remotely hosted Agent task query service. */
public interface AgentTaskCenterToolPort {
    TaskPage mine(ToolActorContext context, TaskQuery query);

    record TaskQuery(String status, LocalDateTime from, LocalDateTime to, int page, int size) { }
    record TaskPage(List<Task> records, long total, int page, int size) {
        public TaskPage { records = List.copyOf(records); }
    }
    record Task(String taskId, String pageId, String status, String riskLevel, int planVersion,
                LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime finishedAt,
                String errorCode) { }
}
