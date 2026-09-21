package com.aiworkmate.agent.tool.adapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the in-process implementation of an Agent domain port. Remote Spring
 * Cloud adapters can replace these beans without changing handlers or ports.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@ConditionalOnProperty(
        prefix = "agent.domain",
        name = "adapter-mode",
        havingValue = "local",
        matchIfMissing = true)
public @interface LocalAgentDomainAdapter { }
