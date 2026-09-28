package com.aiworkmate.agent.tool.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAgentDomainAdapterTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void enablesLocalAdaptersByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(SampleLocalAdapter.class));
    }

    @Test
    void disablesLocalAdaptersWhenRemoteModeIsSelected() {
        contextRunner.withPropertyValues("agent.domain.adapter-mode=remote")
                .run(context -> assertThat(context).doesNotHaveBean(SampleLocalAdapter.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SampleLocalAdapter.class)
    static class TestConfiguration { }

    @LocalAgentDomainAdapter
    static class SampleLocalAdapter { }
}
