package com.quartzkube.core;

import java.util.*;
import java.util.concurrent.*;

/**
 * Stub implementation of a Quartz-compatible scheduler.
 * This basic version only supports in-memory scheduling of Runnable jobs.
 */
public class QuartzKubeScheduler {
    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    private final Map<Class<?>, Queue<Class<?>>> pending = new ConcurrentHashMap<>();
    private final Set<Class<?>> running = ConcurrentHashMap.newKeySet();
    private final JobStore store;
    private volatile boolean started = false;

    public QuartzKubeScheduler() {
        this(new InMemoryJobStore());
    }

    public QuartzKubeScheduler(JobStore store) {
        this.store = store;
    }

    /**
     * Starts the scheduler executor, registers metrics and launches the
     * optional Prometheus endpoint if configured.
     */
    public void start() {
        Metrics.init();
        MetricsServer.init();
        started = true;
        try {
            for (String cls : store.loadJobs()) {
                try {
                    Class<?> c = Class.forName(cls);
                    scheduleJobInternal(c);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            // ignore load errors
        }
    }

    /**
     * Schedule a job class for immediate execution.
     * The job class must implement {@link Runnable} and have a no-arg constructor.
     */
    public void scheduleJob(Class<?> jobClass) {
        if (!started) {
            throw new IllegalStateException("Scheduler not started");
        }
        try {
            store.saveJob(jobClass.getName());
        } catch (Exception ignored) {}
        scheduleJobInternal(jobClass);
    }

    private void scheduleJobInternal(Class<?> jobClass) {
        boolean disallow = jobClass.isAnnotationPresent(DisallowConcurrentExecution.class);
        if (disallow && running.contains(jobClass)) {
            pending.computeIfAbsent(jobClass, k -> new ArrayDeque<>()).add(jobClass);
            return;
        }
        if (disallow) {
            running.add(jobClass);
        }

        executor.submit(() -> {
            try {
                Runnable job = (Runnable) jobClass.getDeclaredConstructor().newInstance();
                job.run();
                Metrics.getInstance().recordSuccess();
            } catch (Exception e) {
                Metrics.getInstance().recordFailure();
                // For now just print the stack trace
                e.printStackTrace();
            } finally {
                if (disallow) {
                    finishNonConcurrent(jobClass);
                }
            }
        });
    }

    private void finishNonConcurrent(Class<?> jobClass) {
        Queue<Class<?>> q = pending.get(jobClass);
        Class<?> next;
        if (q != null && (next = q.poll()) != null) {
            running.remove(jobClass);
            executor.schedule(() -> scheduleJobInternal(next), 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            running.remove(jobClass);
        }
    }

    /** Shuts down the scheduler executor. */
    public void shutdown() {
        executor.shutdownNow();
        started = false;
    }
}
