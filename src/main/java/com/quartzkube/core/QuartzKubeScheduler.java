package com.quartzkube.core;

import java.util.*;
import java.util.concurrent.*;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerListener;
import org.quartz.JobListener;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.CronTrigger;
import org.quartz.SimpleTrigger;
import org.quartz.CronExpression;

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
    private final List<JobListener> jobListeners = new CopyOnWriteArrayList<>();
    private final List<TriggerListener> triggerListeners = new CopyOnWriteArrayList<>();
    private volatile boolean started = false;

    public QuartzKubeScheduler() {
        this(new InMemoryJobStore());
    }

    public QuartzKubeScheduler(JobStore store) {
        this.store = store;
    }

    /** Register a JobListener to receive job events. */
    public void addJobListener(JobListener l) {
        jobListeners.add(l);
    }

    /** Register a TriggerListener to receive trigger events. */
    public void addTriggerListener(TriggerListener l) {
        triggerListeners.add(l);
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

    /**
     * Quartz-compatible API accepting JobDetail and Trigger. Supports
     * {@link CronTrigger} and {@link SimpleTrigger}. Other trigger types
     * result in immediate execution.
     */
    public void scheduleJob(JobDetail detail, Trigger trigger) {
        if (detail == null || trigger == null) {
            throw new IllegalArgumentException("JobDetail and Trigger required");
        }
        Class<?> jobClass = detail.getJobClass();
        if (trigger instanceof CronTrigger cron) {
            scheduleCron(jobClass, cron);
        } else if (trigger instanceof SimpleTrigger st) {
            scheduleSimple(jobClass, st);
        } else {
            scheduleJob(jobClass);
        }
    }

    private void scheduleCron(Class<?> jobClass, CronTrigger cron) {
        CronExpression expr;
        try {
            expr = new CronExpression(cron.getCronExpression());
        } catch (Exception e) {
            scheduleJob(jobClass);
            return;
        }
        Runnable task = new Runnable() {
            @Override public void run() {
                scheduleJobInternal(jobClass, cron);
                Date next = expr.getNextValidTimeAfter(new Date());
                if (next != null) {
                    long delay = next.getTime() - System.currentTimeMillis();
                    executor.schedule(this, delay, TimeUnit.MILLISECONDS);
                }
            }
        };
        Date first = expr.getNextValidTimeAfter(new Date());
        if (first != null) {
            long delay = first.getTime() - System.currentTimeMillis();
            executor.schedule(task, Math.max(delay, 0), TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleSimple(Class<?> jobClass, SimpleTrigger trig) {
        Date start = trig.getStartTime();
        long delay = 0;
        if (start != null) {
            delay = start.getTime() - System.currentTimeMillis();
            if (delay < 0) delay = 0;
        }
        int repeat = trig.getRepeatCount();
        long interval = trig.getRepeatInterval();
        Runnable task = new Runnable() {
            int remaining = repeat;
            @Override public void run() {
                scheduleJobInternal(jobClass, trig);
                if (remaining == SimpleTrigger.REPEAT_INDEFINITELY || remaining-- > 0) {
                    executor.schedule(this, interval, TimeUnit.MILLISECONDS);
                }
            }
        };
        executor.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    private void scheduleJobInternal(Class<?> jobClass) {
        scheduleJobInternal(jobClass, null);
    }

    private void scheduleJobInternal(Class<?> jobClass, Trigger trigger) {
        boolean disallow = jobClass.isAnnotationPresent(DisallowConcurrentExecution.class);
        if (disallow && running.contains(jobClass)) {
            pending.computeIfAbsent(jobClass, k -> new ArrayDeque<>()).add(jobClass);
            return;
        }
        if (disallow) {
            running.add(jobClass);
        }

        executor.submit(() -> {
            for (TriggerListener tl : triggerListeners) {
                try {
                    tl.triggerFired(trigger, null);
                } catch (Exception ignored) {}
            }
            boolean veto = false;
            for (TriggerListener tl : triggerListeners) {
                try {
                    if (tl.vetoJobExecution(trigger, null)) {
                        veto = true;
                    }
                } catch (Exception ignored) {}
            }
            if (veto) {
                for (JobListener jl : jobListeners) {
                    try { jl.jobExecutionVetoed(null); } catch (Exception ignore) {}
                }
                for (TriggerListener tl : triggerListeners) {
                    try { tl.triggerComplete(trigger, null, CompletedExecutionInstruction.NOOP); } catch (Exception ignore) {}
                }
                if (disallow) { finishNonConcurrent(jobClass); }
                return;
            }
            for (JobListener jl : jobListeners) {
                try { jl.jobToBeExecuted(null); } catch (Exception ignore) {}
            }
            Exception err = null;
            try {
                Object obj = jobClass.getDeclaredConstructor().newInstance();
                if (obj instanceof Runnable runnable) {
                    runnable.run();
                } else if (obj instanceof org.quartz.Job qjob) {
                    qjob.execute(null);
                } else {
                    throw new IllegalArgumentException("Job class does not implement Runnable or Job");
                }
                Metrics.getInstance().recordSuccess();
            } catch (Exception e) {
                Metrics.getInstance().recordFailure();
                err = e;
                e.printStackTrace();
            } finally {
                for (JobListener jl : jobListeners) {
                    try { jl.jobWasExecuted(null, err == null ? null : new org.quartz.JobExecutionException(err)); } catch (Exception ignore) {}
                }
                for (TriggerListener tl : triggerListeners) {
                    try { tl.triggerComplete(trigger, null, CompletedExecutionInstruction.NOOP); } catch (Exception ignore) {}
                }
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
