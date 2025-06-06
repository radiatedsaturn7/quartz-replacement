package com.quartzkube.core;

/**
 * Simple listener that prints job outcomes to stdout.
 */
public class PrintJobResultListener implements JobResultListener {
    @Override
    public void jobFinished(String jobClass, boolean success) {
        System.out.println("Job " + jobClass + " finished " + (success ? "successfully" : "with errors"));
    }
}
