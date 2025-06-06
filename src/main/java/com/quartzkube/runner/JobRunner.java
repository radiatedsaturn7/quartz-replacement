package com.quartzkube.runner;

import org.quartz.PersistJobDataAfterExecution;
import com.quartzkube.core.BasicJobExecutionContext;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simple job runner that loads a job class and invokes it. Supports capturing
 * updated JobDataMap when {@link org.quartz.PersistJobDataAfterExecution} is present.
 */
public class JobRunner {
    public static void main(String[] args) throws Exception {
        String className;
        if (args.length > 0) {
            className = args[0];
        } else {
            className = System.getenv("JOB_CLASS");
            if (className == null || className.isEmpty()) {
                System.err.println("No job class specified via args or JOB_CLASS env");
                return;
            }
        }

        String dataArg = args.length > 1 ? args[1] : System.getenv("JOB_DATA");
        Map<String, String> data = parseData(dataArg);

        Class<?> clazz = Class.forName(className);
        Object obj = clazz.getDeclaredConstructor().newInstance();

        if (obj instanceof Job qjob) {
            JobDataMap initMap = new JobDataMap(new HashMap<>(data));
            var detail = JobBuilder.newJob((Class<? extends Job>) clazz)
                    .withIdentity("job")
                    .usingJobData(initMap)
                    .build();
            JobDataMap map = detail.getJobDataMap();
            BasicJobExecutionContext ctx = new BasicJobExecutionContext(detail, map);
            long start = System.currentTimeMillis();
            qjob.execute(ctx);
            ctx.setJobRunTime(System.currentTimeMillis() - start);
            if (clazz.isAnnotationPresent(org.quartz.PersistJobDataAfterExecution.class)) {
                System.out.println("UPDATED_JOB_DATA:" + encode(map));
            }
        } else if (obj instanceof Runnable runnable) {
            runnable.run();
        } else {
            throw new IllegalArgumentException("Job class does not implement Runnable or Job");
        }
    }

    private static Map<String, String> parseData(String str) {
        Map<String, String> map = new HashMap<>();
        if (str == null || str.isEmpty()) return map;
        for (String pair : str.split(";")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return map;
    }

    private static String encode(JobDataMap map) {
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }
}
