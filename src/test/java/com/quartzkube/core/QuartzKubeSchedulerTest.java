package com.quartzkube.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QuartzKubeSchedulerTest {

    public static class CounterJob implements Runnable {
        static int count = 0;
        @Override
        public void run() {
            count++;
        }
    }

    @Test
    public void testScheduleJobRuns() throws Exception {
        QuartzKubeScheduler scheduler = new QuartzKubeScheduler();
        scheduler.start();
        scheduler.scheduleJob(CounterJob.class);
        // Wait briefly for execution
        Thread.sleep(100);
        scheduler.shutdown();
        assertEquals(1, CounterJob.count);
    }

    @DisallowConcurrentExecution
    public static class SlowJob implements Runnable {
        static int count = 0;
        @Override
        public void run() {
            count++;
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
        }
    }

    @Test
    public void testDisallowConcurrentExecutionQueuesJob() throws Exception {
        QuartzKubeScheduler scheduler = new QuartzKubeScheduler();
        scheduler.start();
        scheduler.scheduleJob(SlowJob.class);
        scheduler.scheduleJob(SlowJob.class);
        Thread.sleep(250); // during first run, second should not have executed yet
        assertEquals(1, SlowJob.count);
        Thread.sleep(250); // allow second run to finish
        scheduler.shutdown();
        assertEquals(2, SlowJob.count);
    }
}
