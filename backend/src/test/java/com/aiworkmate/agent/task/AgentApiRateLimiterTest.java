package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentApiRateLimiterTest {
    @Test void intentHasItsOwnBoundedBucketPerUserAndTenant() {
        AgentApiRateLimiter limiter = new AgentApiRateLimiter();
        for (int i = 0; i < 30; i++) limiter.checkIntent(1, 7);
        assertThatThrownBy(() -> limiter.checkIntent(1, 7)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RATE_LIMITED");
        limiter.checkPlan(1, 7);
        limiter.checkIntent(1, 8);
        limiter.checkIntent(2, 7);
    }
    @Test
    void enforcesIndependentPlanAndExecuteLimitsPerOwner() {
        AgentApiRateLimiter limiter = new AgentApiRateLimiter();
        for (int i = 0; i < 10; i++) limiter.checkPlan(1, 7);
        assertThatThrownBy(() -> limiter.checkPlan(1, 7)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RATE_LIMITED");
        limiter.checkPlan(1, 8);

        for (int i = 0; i < 5; i++) limiter.checkExecute(1, 7);
        assertThatThrownBy(() -> limiter.checkExecute(1, 7)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RATE_LIMITED");
    }
}
