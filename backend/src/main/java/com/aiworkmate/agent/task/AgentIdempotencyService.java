package com.aiworkmate.agent.task;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentIdempotencyService {
    private final AgentIdempotencyMapper mapper;

    @Transactional
    public IdempotencyBinding bind(Long tenantId,
                                   Long userId,
                                   IdempotencyOperation operation,
                                   String idempotencyKey,
                                   String requestHash,
                                   Long taskId) {
        validateKey(idempotencyKey);
        int inserted = mapper.insertIfAbsent(
                tenantId, userId, operation.name(), idempotencyKey, requestHash, taskId
        );
        if (inserted == 1) {
            return new IdempotencyBinding(true, taskId);
        }
        AgentIdempotency existing = mapper.selectDomainKey(
                tenantId, userId, operation.name(), idempotencyKey
        );
        if (existing == null || !requestHash.equals(existing.getRequestHash())) {
            throw new IdempotencyConflictException();
        }
        return new IdempotencyBinding(false, existing.getTaskId());
    }

    private void validateKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 128 || key.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }
    }
}
