package com.quartzkube.core;

import java.util.List;

/**
 * Simple interface for persisting scheduled jobs.
 */
public interface JobStore {
    /** Persist a job class name for execution. */
    void saveJob(String jobClass) throws Exception;

    /** Load all persisted job class names. */
    List<String> loadJobs() throws Exception;
}
