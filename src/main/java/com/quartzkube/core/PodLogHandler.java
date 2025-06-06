package com.quartzkube.core;

/**
 * Receives log lines from a Kubernetes pod.
 */
public interface PodLogHandler {
    /**
     * Handle a single log line for the given job.
     *
     * @param jobClass fully qualified job class name
     * @param line log line from the pod
     */
    void handle(String jobClass, String line);
}
