package com.quartzkube.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class QuartzKubeSchedulerFactoryTest {
    @Test
    public void testLoadsPropertiesFile() throws Exception {
        Path file = Files.createTempFile("qk", ".properties");
        Properties p = new Properties();
        p.setProperty("JOB_IMAGE", "fromFile");
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "");
        }
        QuartzKubeSchedulerFactory f = new QuartzKubeSchedulerFactory(file.toString(), false);
        f.getScheduler();
        assertEquals("fromFile", System.getProperty("JOB_IMAGE"));
    }

    @Test
    public void testHotReload() throws Exception {
        Path file = Files.createTempFile("qk", ".properties");
        Properties p = new Properties();
        p.setProperty("JOB_IMAGE", "one");
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "");
        }
        QuartzKubeSchedulerFactory f = new QuartzKubeSchedulerFactory(file.toString(), true);
        f.getScheduler();
        assertEquals("one", System.getProperty("JOB_IMAGE"));

        Thread.sleep(200);
        p.setProperty("JOB_IMAGE", "two");
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "");
        }
        // give watcher time
        Thread.sleep(500);
        assertEquals("two", System.getProperty("JOB_IMAGE"));
    }
}
