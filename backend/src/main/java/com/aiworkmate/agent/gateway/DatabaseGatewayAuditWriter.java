package com.aiworkmate.agent.gateway;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatabaseGatewayAuditWriter implements GatewayAuditWriter {
    private final AgentToolInvocationMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String record(GatewayExecutionSnapshot snapshot,
                         GatewayDecision decision,
                         String decisionCode,
                         boolean handlerInvoked) {
        AgentToolInvocation invocation = new AgentToolInvocation();
        invocation.setDecisionId(UUID.randomUUID().toString());
        invocation.setTenantId(snapshot.getTenantId());
        invocation.setUserId(snapshot.getUserId());
        invocation.setTaskId(snapshot.getTaskId());
        invocation.setStepId(snapshot.getStepId());
        invocation.setAttempt(snapshot.getStepAttempt());
        invocation.setToolCode(snapshot.getToolCode());
        invocation.setToolVersion(snapshot.getToolVersion());
        invocation.setDecision(decision.name());
        invocation.setDecisionCode(decisionCode);
        invocation.setArgsHash(snapshot.getArgsHash());
        invocation.setHandlerInvoked(handlerInvoked);
        invocation.setTraceId(snapshot.getTraceId());
        if (mapper.insert(invocation) != 1) {
            throw new IllegalStateException("Gateway audit insert failed");
        }
        return invocation.getDecisionId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String decisionId,
                         boolean handlerInvoked,
                         String outcome,
                         Integer resultBytes,
                         String errorClass,
                         long durationMs) {
        LambdaUpdateWrapper<AgentToolInvocation> update = new LambdaUpdateWrapper<>();
        update.eq(AgentToolInvocation::getDecisionId, decisionId)
                .isNull(AgentToolInvocation::getCompletedAt)
                .set(AgentToolInvocation::getHandlerInvoked, handlerInvoked)
                .set(AgentToolInvocation::getOutcome, outcome)
                .set(AgentToolInvocation::getResultBytes, resultBytes)
                .set(AgentToolInvocation::getErrorClass, errorClass)
                .set(AgentToolInvocation::getDurationMs, durationMs)
                .set(AgentToolInvocation::getCompletedAt, LocalDateTime.now());
        if (mapper.update(null, update) != 1) {
            throw new IllegalStateException("Gateway audit completion failed");
        }
    }
}
