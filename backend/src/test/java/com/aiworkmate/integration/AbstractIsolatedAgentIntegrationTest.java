package com.aiworkmate.integration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Close consumers/pools before deleting only the fixture-owned database and Redis process. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = AbstractIsolatedAgentIntegrationTest.ResourceListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractIsolatedAgentIntegrationTest {
    protected abstract AgentIntegrationEnvironment environment();

    public static final class ResourceListener extends AbstractTestExecutionListener {
        private static final String RESOURCE = ResourceListener.class.getName() + ".environment";
        // Register ownership before dependency injection (2000) can fail to load the context.
        // afterTestClass is reversed: DirtiesContext (3000) still closes consumers first.
        @Override public int getOrder() { return 1999; }

        @Override public void prepareTestInstance(TestContext testContext) {
            var owner = (AbstractIsolatedAgentIntegrationTest) testContext.getTestInstance();
            testContext.setAttribute(RESOURCE, owner.environment());
            var context = testContext.getApplicationContext();
            owner.environment().verifyBindings(context.getBean(JdbcTemplate.class),
                    context.getBean(StringRedisTemplate.class));
        }

        @Override public void afterTestClass(TestContext testContext) {
            // Do not reload the application context after DirtiesContext has removed it.
            var environment = (AgentIntegrationEnvironment) testContext.removeAttribute(RESOURCE);
            if (environment != null) environment.close();
        }
    }
}
