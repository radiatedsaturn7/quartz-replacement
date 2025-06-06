package com.quartzkube.examples;

import com.quartzkube.core.QuartzKubeScheduler;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        QuartzKubeScheduler scheduler = new QuartzKubeScheduler();
        scheduler.start();
        scheduler.scheduleJob(HelloWorldJob.class);
        // give the job some time to run
        Thread.sleep(1000);
        scheduler.shutdown();
    }
}
