package com.quartzkube.runner;

import org.quartz.PersistJobDataAfterExecution;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import static org.junit.jupiter.api.Assertions.*;

public class JobRunnerTest {

    @PersistJobDataAfterExecution
    public static class CountingJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
            var map = context.getJobDetail().getJobDataMap();
            int count = map.containsKey("count") ? map.getInt("count") : 0;
            map.put("count", count + 1);
        }
    }

    @Test
    public void testPersistJobDataAfterExecution() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream orig = System.out;
        System.setOut(new java.io.PrintStream(out));
        try {
            JobRunner.main(new String[]{CountingJob.class.getName(), "count=1"});
        } finally {
            System.out.flush();
            System.setOut(orig);
        }
        String output = out.toString();
        assertTrue(output.contains("UPDATED_JOB_DATA:"));
        assertTrue(output.contains("count=2"));
    }
}
