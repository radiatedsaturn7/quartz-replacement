package com.quartzkube.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log handler that forwards pod logs to an SLF4J logger named after the job class.
 */
public class Slf4jLogHandler implements PodLogHandler {
    @Override
    public void handle(String jobClass, String line) {
        Logger log = LoggerFactory.getLogger(jobClass);
        log.info(line);
    }
}
