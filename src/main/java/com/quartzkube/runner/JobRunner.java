package com.quartzkube.runner;

/**
 * Simple job runner that loads a job class and invokes it.
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

        Class<?> clazz = Class.forName(className);
        Runnable job = (Runnable) clazz.getDeclaredConstructor().newInstance();
        job.run();
    }
}
