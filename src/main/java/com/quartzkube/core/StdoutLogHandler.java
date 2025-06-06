package com.quartzkube.core;

/**
 * Default log handler that writes pod logs to stdout.
 */
public class StdoutLogHandler implements PodLogHandler {
    @Override
    public void handle(String jobClass, String line) {
        System.out.println(line);
    }
}
