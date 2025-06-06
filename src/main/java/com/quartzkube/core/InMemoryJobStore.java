package com.quartzkube.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple in-memory JobStore used by default.
 */
public class InMemoryJobStore implements JobStore {
    private final List<String> jobs = new ArrayList<>();

    @Override
    public synchronized void saveJob(String jobClass) {
        jobs.add(jobClass);
    }

    @Override
    public synchronized List<String> loadJobs() {
        return new ArrayList<>(jobs);
    }
}
