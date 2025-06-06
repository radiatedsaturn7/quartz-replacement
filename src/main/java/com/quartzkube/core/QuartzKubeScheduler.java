package com.quartzkube.core;

import java.util.*;
import java.util.concurrent.*;

import com.quartzkube.core.LeaderElection;
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
    private LeaderElection leaderElection;
    private final List<JobListener> jobListeners = new CopyOnWriteArrayList<>();
    private final List<TriggerListener> triggerListeners = new CopyOnWriteArrayList<>();
    private volatile boolean started = false;

    public QuartzKubeScheduler() {
        this(new InMemoryJobStore());
    }

    public QuartzKubeScheduler(JobStore store) {
        this.store = store;
    }

    private static String getConfig(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isEmpty()) {
            v = System.getenv(key);
        }
        return v == null || v.isEmpty() ? def : v;
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
        String enable = getConfig("ENABLE_LEADER_ELECTION", "false");
        if (Boolean.parseBoolean(enable)) {
            String apiUrl = getConfig("KUBE_API_URL", "http://localhost:8001");
            String ns = getConfig("JOB_NAMESPACE", "default");
            String name = getConfig("LEASE_NAME", "quartzkube-leader");
            leaderElection = new LeaderElection(apiUrl, ns, name);
            leaderElection.start();
        }
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
        if (leaderElection != null && !leaderElection.isLeader()) {
            return;
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
        if (leaderElection != null && !leaderElection.isLeader()) {
            return;
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

        Date now = new Date();
        if (cron.getStartTime() != null && cron.getStartTime().before(now)) {
            int instr = cron.getMisfireInstruction();
            if (instr == CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW) {
                scheduleJobInternal(jobClass, cron);
            }
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

        Date first = expr.getNextValidTimeAfter(now);
        if (first != null) {
            long delay = first.getTime() - System.currentTimeMillis();
            executor.schedule(task, Math.max(delay, 0), TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleSimple(Class<?> jobClass, SimpleTrigger trig) {
        Date start = trig.getStartTime();
        long now = System.currentTimeMillis();
        long delay = 0;
        if (start != null) {
            delay = start.getTime() - now;
        }

        int repeat = trig.getRepeatCount();
        long interval = trig.getRepeatInterval();

        if (delay < 0) {
            int instr = trig.getMisfireInstruction();
            if (instr == SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW) {
                scheduleJobInternal(jobClass, trig);
                if (repeat != SimpleTrigger.REPEAT_INDEFINITELY) {
                    repeat--;
                }
                delay = interval > 0 ? interval : 0;
            } else if (interval > 0) {
                long missed = (-delay) / interval + 1;
                delay = interval - ((-delay) % interval);
                if (repeat != SimpleTrigger.REPEAT_INDEFINITELY) {
                    repeat = Math.max(0, repeat - (int) missed);
                }
            } else {
                delay = 0;
            }
        }

        final int startRemaining = repeat;
        Runnable task = new Runnable() {
            int remaining = startRemaining;
            @Override public void run() {
                scheduleJobInternal(jobClass, trig);
                if (remaining == SimpleTrigger.REPEAT_INDEFINITELY || remaining-- > 0) {
                    executor.schedule(this, interval, TimeUnit.MILLISECONDS);
                }
            }
        };

        executor.schedule(task, Math.max(delay, 0), TimeUnit.MILLISECONDS);
    }

    private void scheduleJobInternal(Class<?> jobClass) {
        scheduleJobInternal(jobClass, null);
    }

    private void scheduleJobInternal(Class<?> jobClass, Trigger trigger) {
        if (leaderElection != null && !leaderElection.isLeader()) {
            return;
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
            long startTime = System.currentTimeMillis();
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
                Metrics.getInstance().recordDuration(System.currentTimeMillis() - startTime);
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
        if (leaderElection != null) {
            leaderElection.stop();
        }
        started = false;
    }
}
