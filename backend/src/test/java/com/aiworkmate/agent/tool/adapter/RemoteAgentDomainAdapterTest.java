package com.aiworkmate.agent.tool.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteAgentDomainAdapterTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void keepsRemoteAdaptersDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(SampleRemoteAdapter.class));
    }

    @Test
    void enablesRemoteAdaptersOnlyInRemoteMode() {
        contextRunner.withPropertyValues("agent.domain.adapter-mode=remote")
                .run(context -> assertThat(context).hasSingleBean(SampleRemoteAdapter.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SampleRemoteAdapter.class)
    static class TestConfiguration { }

    @RemoteAgentDomainAdapter
    static class SampleRemoteAdapter { }
}
