package com.aiworkmate.agent.tool.adapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a fixed-target authenticated remote implementation of a domain port.
 * Remote adapters remain internal infrastructure and never expose ToolGateway.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@ConditionalOnProperty(prefix = "agent.domain", name = "adapter-mode", havingValue = "remote")
public @interface RemoteAgentDomainAdapter { }
