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
    private volatile boolean started = false;

    /** Starts the scheduler executor. */
    public void start() {
        started = true;
    }

    /**
     * Schedule a job class for immediate execution.
     * The job class must implement {@link Runnable} and have a no-arg constructor.
     */
    public void scheduleJob(Class<?> jobClass) {
        if (!started) {
            throw new IllegalStateException("Scheduler not started");
        }

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
            } catch (Exception e) {
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
            executor.submit(() -> scheduleJob(next));
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
