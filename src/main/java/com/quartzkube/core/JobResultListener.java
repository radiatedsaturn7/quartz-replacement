package com.quartzkube.core;

/**
 * Listener notified when a dispatched job finishes.
 */
public interface JobResultListener {
    /**
     * Invoked after a job completes.
     *
     * @param jobClass fully qualified job class name
     * @param success  true if job succeeded
     */
    void jobFinished(String jobClass, boolean success);
}
