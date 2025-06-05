package com.quartzkube.core;

/**
 * JMX interface for job execution metrics.
 */
public interface JobMetricsMBean {
    int getDispatchedCount();
    int getSucceededCount();
    int getFailedCount();
}
