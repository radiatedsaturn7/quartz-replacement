package com.quartzkube.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/**
 * Creates QuartzKubeScheduler instances using configuration from
 * environment variables, system properties and an optional properties file.
 * When hotReload is enabled the properties file is watched for changes
 * and properties are reloaded automatically.
 */
public class QuartzKubeSchedulerFactory {
    private final Path propsPath;
    private final boolean hotReload;
    private long lastModified;

    public QuartzKubeSchedulerFactory() {
        this(getDefaultPath(), Boolean.parseBoolean(getProp("HOT_RELOAD", "false")));
    }

    public QuartzKubeSchedulerFactory(String path, boolean hotReload) {
        this(Paths.get(path), hotReload);
    }

    public QuartzKubeSchedulerFactory(Path path, boolean hotReload) {
        this.propsPath = path;
        this.hotReload = hotReload;
        loadProps();
        if (hotReload && Files.exists(propsPath)) {
            startWatcher();
        }
    }

    /** Returns a new scheduler using the current properties. */
    public QuartzKubeScheduler getScheduler() {
        loadProps();
        return new QuartzKubeScheduler();
    }

    private static Path getDefaultPath() {
        String p = System.getProperty("quartzkube.properties");
        if (p == null || p.isEmpty()) {
            p = System.getenv("QUARTZKUBE_PROPERTIES");
        }
        if (p == null || p.isEmpty()) {
            p = "quartzkube.properties";
        }
        return Paths.get(p);
    }

    private static String getProp(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isEmpty()) {
            v = System.getenv(key);
        }
        return v == null || v.isEmpty() ? def : v;
    }

    private synchronized void loadProps() {
        if (Files.exists(propsPath)) {
            Properties p = new Properties();
            try (var in = Files.newInputStream(propsPath)) {
                p.load(in);
                for (String name : p.stringPropertyNames()) {
                    System.setProperty(name, p.getProperty(name));
                }
            } catch (IOException ignored) {}
            try {
                lastModified = Files.getLastModifiedTime(propsPath).toMillis();
            } catch (IOException ignored) {}
        }
    }

    private void startWatcher() {
        Thread t = new Thread(() -> {
            try {
                WatchService ws = FileSystems.getDefault().newWatchService();
                Path dir = propsPath.toAbsolutePath().getParent();
                if (dir == null) return;
                dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    WatchKey k = ws.take();
                    for (WatchEvent<?> e : k.pollEvents()) {
                        Path changed = (Path) e.context();
                        if (changed != null && changed.endsWith(propsPath.getFileName())) {
                            long lm = Files.getLastModifiedTime(propsPath).toMillis();
                            if (lm != lastModified) {
                                loadProps();
                            }
                        }
                    }
                    k.reset();
                }
            } catch (Exception ignore) {}
        });
        t.setDaemon(true);
        t.start();
    }
}
