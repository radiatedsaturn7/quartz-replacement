package com.quartzkube.core;

/**
 * JMX MBean interface exposing basic job execution metrics.
 */
public interface MetricsMBean {
    int getSuccessCount();
    int getFailureCount();
}
