package com.quartzkube.core;

import org.quartz.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal implementation of {@link JobExecutionContext} used by the JobRunner.
 */
public class BasicJobExecutionContext implements JobExecutionContext {
    private final JobDetail jobDetail;
    private final JobDataMap dataMap;
    private final Map<Object, Object> context = new HashMap<>();
    private Object result;
    private long runTime;

    public BasicJobExecutionContext(JobDetail detail, JobDataMap map) {
        this.jobDetail = detail;
        this.dataMap = map;
    }

    @Override public Scheduler getScheduler() { return null; }
    @Override public Trigger getTrigger() { return null; }
    @Override public org.quartz.Calendar getCalendar() { return null; }
    @Override public boolean isRecovering() { return false; }
    @Override public TriggerKey getRecoveringTriggerKey() { throw new IllegalStateException(); }
    @Override public int getRefireCount() { return 0; }
    @Override public JobDataMap getMergedJobDataMap() { return dataMap; }
    @Override public JobDetail getJobDetail() { return jobDetail; }
    @Override public Job getJobInstance() { return null; }
    @Override public Date getFireTime() { return new Date(); }
    @Override public Date getScheduledFireTime() { return null; }
    @Override public Date getPreviousFireTime() { return null; }
    @Override public Date getNextFireTime() { return null; }
    @Override public String getFireInstanceId() { return null; }
    @Override public Object getResult() { return result; }
    @Override public void setResult(Object result) { this.result = result; }
    @Override public long getJobRunTime() { return runTime; }
    public void setJobRunTime(long rt) { this.runTime = rt; }
    @Override public void put(Object key, Object value) { context.put(key, value); }
    @Override public Object get(Object key) { return context.get(key); }
}
