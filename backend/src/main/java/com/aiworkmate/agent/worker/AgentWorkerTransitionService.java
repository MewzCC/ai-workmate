package com.aiworkmate.agent.worker;

import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskEventService;
import com.aiworkmate.agent.task.AgentTaskStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentWorkerTransitionService {
    private final AgentWorkerMapper mapper;
    private final AgentTaskEventService eventService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentTaskStep startNextStep(AgentTask task, String workerId, String leaseHash,
                                       LocalDateTime stepTimeout) {
        AgentTaskStep step = mapper.startNextStep(task.getId(), workerId, leaseHash,
                task.getAttemptCount(), stepTimeout);
        if (step != null) {
            eventService.publish(task.getId(), "step-started",
                    objectMapper.createObjectNode().put("sequence", step.getSequenceNo())
                            .put("toolCode", step.getToolCode()).put("status", "RUNNING"), step.getTraceId());
        }
        return step;
    }

    @Transactional
    public boolean completeStep(AgentTask task, AgentTaskStep step, String workerId,
                                String leaseHash, String result) {
        if (mapper.completeStep(step.getId(), task.getAttemptCount(), workerId, leaseHash, result) != 1) return false;
        eventService.publish(task.getId(), "step-completed",
                objectMapper.createObjectNode().put("sequence", step.getSequenceNo())
                        .put("toolCode", step.getToolCode()).put("status", "SUCCEEDED"), step.getTraceId());
        return true;
    }

    @Transactional
    public void completeTask(AgentTask task, String workerId, String leaseHash) {
        if (mapper.completeTask(task.getId(), workerId, leaseHash) == 1) {
            eventService.publish(task.getId(), "task-completed",
                    objectMapper.createObjectNode().put("taskId", task.getTaskNo()).put("status", "SUCCEEDED"),
                    task.getTraceId());
        }
    }

    @Transactional
    public boolean retryReadOnly(AgentTask task, AgentTaskStep step, String workerId, String leaseHash) {
        if (mapper.retryReadOnly(step.getId(), task.getAttemptCount(), workerId, leaseHash) != 1) return false;
        eventService.publish(task.getId(), "snapshot",
                objectMapper.createObjectNode().put("taskId", task.getTaskNo()).put("status", "QUEUED"),
                task.getTraceId());
        return true;
    }

    @Transactional
    public void fail(AgentTask task, AgentTaskStep step, String workerId, String leaseHash,
                     String errorCode) {
        if (mapper.fail(step.getId(), task.getAttemptCount(), workerId, leaseHash,
                errorCode, "Tool execution rejected") == 1) {
            eventService.publish(task.getId(), "task-failed",
                    objectMapper.createObjectNode().put("taskId", task.getTaskNo())
                            .put("status", "FAILED").put("errorCode", errorCode), task.getTraceId());
        }
    }

    @Transactional
    public void markOutcomeUnknown(AgentTask task, AgentTaskStep step,
                                   String workerId, String leaseHash) {
        if (mapper.markOutcomeUnknown(step.getId(), task.getAttemptCount(), workerId, leaseHash) == 1) {
            eventService.publish(task.getId(), "task-completed",
                    objectMapper.createObjectNode().put("taskId", task.getTaskNo())
                            .put("status", "PARTIALLY_SUCCEEDED")
                            .put("errorCode", "TOOL_RESULT_UNKNOWN"), task.getTraceId());
        }
    }
}
