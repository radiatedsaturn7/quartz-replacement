package com.quartzkube.core;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple metrics registry that tracks job execution counts and exposes them via JMX.
 */
public final class Metrics implements MetricsMBean {
    private static final Metrics INSTANCE = new Metrics();

    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();

    private Metrics() {}

    /**
     * Returns the singleton metrics instance.
     */
    public static Metrics getInstance() {
        return INSTANCE;
    }

    /**
     * Registers the Metrics MBean with the platform MBean server if not already registered.
     */
    public static void init() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.quartzkube.core:type=Metrics");
            if (!server.isRegistered(name)) {
                server.registerMBean(INSTANCE, name);
            }
        } catch (Exception ignored) {
            // ignore registration failures
        }
    }

    /** Record a successful job execution. */
    public void recordSuccess() {
        successCount.incrementAndGet();
    }

    /** Record a failed job execution. */
    public void recordFailure() {
        failureCount.incrementAndGet();
    }

    @Override
    public int getSuccessCount() {
        return successCount.get();
    }

    @Override
    public int getFailureCount() {
        return failureCount.get();
    }

    static void reset() {
        INSTANCE.successCount.set(0);
        INSTANCE.failureCount.set(0);
    }
}
