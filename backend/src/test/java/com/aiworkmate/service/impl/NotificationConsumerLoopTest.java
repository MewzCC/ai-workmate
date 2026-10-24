package com.aiworkmate.service.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationConsumerLoopTest {
    @Test
    void failuresBackOffToThirtySecondsAndStopWhenInterrupted() {
        var delays = new ArrayList<Long>();
        var calls = new AtomicInteger();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(NotificationConsumerLoop.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            new NotificationConsumerLoop(() -> {
                calls.incrementAndGet();
                throw new IllegalStateException("private connection details");
            }, () -> true, millis -> {
                delays.add(millis);
                if (delays.size() == 8) throw new InterruptedException();
            }).run();
            assertThat(delays).containsExactly(1000L, 2000L, 4000L, 8000L, 16000L,
                    30000L, 30000L, 30000L);
            assertThat(calls).hasValue(8);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            var warnings = events.list.stream()
                    .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN).toList();
            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).getFormattedMessage()).contains("IllegalStateException")
                    .doesNotContain("private connection details");
            assertThat(warnings.get(0).getThrowableProxy()).isNull();
        } finally {
            Thread.interrupted();
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void successfulEmptyPollResetsBackoffAndUsesIdleDelay() {
        var calls = new AtomicInteger();
        var delays = new ArrayList<Long>();
        try {
            new NotificationConsumerLoop(() -> {
                if (calls.incrementAndGet() == 3) return false;
                throw new IllegalStateException();
            }, () -> true, millis -> {
                delays.add(millis);
                if (delays.size() == 4) throw new InterruptedException();
            }).run();
            assertThat(delays).containsExactly(1000L, 2000L, 200L, 1000L);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void consumedItemIsFollowedImmediatelyByNextPoll() {
        var calls = new AtomicInteger();
        var delays = new ArrayList<Long>();
        var running = new AtomicBoolean(true);
        new NotificationConsumerLoop(() -> {
            if (calls.incrementAndGet() == 2) running.set(false);
            return true;
        }, running::get, delays::add).run();
        assertThat(calls).hasValue(2);
        assertThat(delays).isEmpty();
    }

    @Test
    void shutdownDuringDriverFailureDoesNotRetryOrSleep() {
        var running = new AtomicBoolean(true);
        var delays = new ArrayList<Long>();
        var calls = new AtomicInteger();
        new NotificationConsumerLoop(() -> {
            calls.incrementAndGet();
            running.set(false);
            throw new IllegalStateException();
        }, running::get, delays::add).run();
        assertThat(calls).hasValue(1);
        assertThat(delays).isEmpty();
    }
}
