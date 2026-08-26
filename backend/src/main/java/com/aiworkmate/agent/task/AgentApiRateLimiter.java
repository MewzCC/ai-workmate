package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentApiRateLimiter {
    private static final int PLAN_PER_MINUTE = 10;
    private static final int EXECUTE_PER_MINUTE = 5;
    private final Map<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();

    public void checkPlan(long tenantId, long userId) {
        check("PLAN", tenantId, userId, PLAN_PER_MINUTE);
    }

    public void checkExecute(long tenantId, long userId) {
        check("EXECUTE", tenantId, userId, EXECUTE_PER_MINUTE);
    }

    @Scheduled(fixedDelay = 60_000L)
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(60);
        attempts.forEach((key, values) -> {
            synchronized (values) {
                evict(values, cutoff);
                if (values.isEmpty()) attempts.remove(key, values);
            }
        });
    }

    private void check(String operation, long tenantId, long userId, int limit) {
        String key = operation + ':' + tenantId + ':' + userId;
        ArrayDeque<Instant> values = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (values) {
            evict(values, Instant.now().minusSeconds(60));
            if (values.size() >= limit) throw new BusinessException(ErrorCode.RATE_LIMITED);
            values.addLast(Instant.now());
        }
    }

    private void evict(ArrayDeque<Instant> values, Instant cutoff) {
        while (!values.isEmpty() && values.peekFirst().isBefore(cutoff)) values.removeFirst();
    }
}
