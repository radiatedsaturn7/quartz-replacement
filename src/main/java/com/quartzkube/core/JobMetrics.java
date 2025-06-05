package com.quartzkube.core;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple metrics registry exposed via JMX.
 */
public class JobMetrics implements JobMetricsMBean {
    private static final JobMetrics INSTANCE = new JobMetrics();
    private final AtomicInteger dispatched = new AtomicInteger();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    static {
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(
                INSTANCE,
                new javax.management.ObjectName("com.quartzkube:type=JobMetrics")
            );
        } catch (Exception ignored) {
        }
    }

    public static void recordDispatch() {
        INSTANCE.dispatched.incrementAndGet();
    }

    public static void recordSuccess() {
        INSTANCE.succeeded.incrementAndGet();
    }

    public static void recordFailure() {
        INSTANCE.failed.incrementAndGet();
    }

    public static int getDispatchedCount() {
        return INSTANCE.dispatched.get();
    }

    public static int getSucceededCount() {
        return INSTANCE.succeeded.get();
    }

    public static int getFailedCount() {
        return INSTANCE.failed.get();
    }

    /** Resets all counters (primarily for tests). */
    public static void reset() {
        INSTANCE.dispatched.set(0);
        INSTANCE.succeeded.set(0);
        INSTANCE.failed.set(0);
    }
}
