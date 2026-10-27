package com.aiworkmate.service.impl;

import lombok.extern.slf4j.Slf4j;

import java.util.function.BooleanSupplier;

/** Owns retry timing independently of Redis and Spring lifecycle. */
@Slf4j
final class NotificationConsumerLoop implements Runnable {
    @FunctionalInterface
    interface Sleeper {
        void pause(long millis) throws InterruptedException;
    }

    private final BooleanSupplier poll;
    private final BooleanSupplier running;
    private final Sleeper sleeper;

    NotificationConsumerLoop(BooleanSupplier poll, BooleanSupplier running, Sleeper sleeper) {
        this.poll = poll;
        this.running = running;
        this.sleeper = sleeper;
    }

    @Override
    public void run() {
        int failures = 0;
        while (running.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
            try {
                long delay;
                try {
                    boolean consumed = poll.getAsBoolean();
                    if (failures > 0) log.info("Notification queue polling recovered");
                    failures = 0;
                    delay = consumed ? 0 : 200;
                } catch (RuntimeException ex) {
                    if (!running.getAsBoolean() || Thread.currentThread().isInterrupted()) break;
                    if (failures == 0) {
                        log.warn("Notification queue polling failed; retrying with capped backoff, errorType={}",
                                ex.getClass().getSimpleName());
                    }
                    failures = Math.min(failures + 1, 6);
                    delay = Math.min(1000L << (failures - 1), 30000L);
                }
                if (delay > 0 && running.getAsBoolean()) sleeper.pause(delay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Notification consumer stopped");
    }
}
