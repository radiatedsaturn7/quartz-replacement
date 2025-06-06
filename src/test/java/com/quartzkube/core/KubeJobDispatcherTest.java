package com.quartzkube.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class KubeJobDispatcherTest {
    static {
        System.setProperty("USE_WATCH", "false");
        System.setProperty("STREAM_LOGS", "false");
    }
    public static class LocalJob implements Runnable {
        static int count = 0;
        @Override
        public void run() {
            count++;
        }
    }

    public static class SlowLocalJob implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
        }
    }

    public static class LoggingJob implements Runnable {
        @Override
        public void run() {
            // no-op
        }
    }

    @Test
    public void testLocalDispatchRunsJob() {
        KubeJobDispatcher dispatcher = new KubeJobDispatcher(true);
        LocalJob.count = 0;
        dispatcher.dispatchJob(LocalJob.class.getName());
        assertEquals(1, LocalJob.count);
    }

    @Test
    public void testLogStreaming() throws Exception {
        System.setProperty("STREAM_LOGS", "true");
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        java.util.List<String> paths = new java.util.ArrayList<>();
        server.createContext("/apis/batch/v1/namespaces/test/jobs", ex -> {
            paths.add(ex.getRequestURI().getPath());
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        server.createContext("/api/v1/namespaces/test/pods/com.example.dummyjob/log", ex -> {
            paths.add(ex.getRequestURI().getPath());
            ex.sendResponseHeaders(200, 0);
            ex.getResponseBody().write("log line\n".getBytes());
            ex.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream orig = System.out;
        System.setOut(new java.io.PrintStream(out));
        try {
            KubeJobDispatcher dispatcher = new KubeJobDispatcher(false, url, "test");
            dispatcher.dispatchJob("com.example.DummyJob");
        } finally {
            System.setOut(orig);
            server.stop(0);
            System.setProperty("STREAM_LOGS", "false");
        }
        assertTrue(paths.contains("/api/v1/namespaces/test/pods/com.example.dummyjob/log"));
        assertTrue(out.toString().contains("log line"));
    }

    @Test
    public void testDispatchHitsApi() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        java.util.List<String> paths = new java.util.ArrayList<>();
        server.createContext("/apis/batch/v1/namespaces/test/jobs", ex -> {
            paths.add(ex.getRequestURI().getPath());
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        try {
            KubeJobDispatcher dispatcher = new KubeJobDispatcher(false, url, "test");
            dispatcher.dispatchJob("com.example.DummyJob");
        } finally {
            server.stop(0);
        }
        assertEquals(1, paths.size());
    }

    @Test
    public void testImageOverride() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        final String[] body = new String[1];
        server.createContext("/apis/batch/v1/namespaces/test/jobs", ex -> {
            body[0] = new String(ex.getRequestBody().readAllBytes());
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        try {
            KubeJobDispatcher dispatcher = new KubeJobDispatcher(false, url, "test");
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("k8sImage", "custom/image:1");
            dispatcher.dispatchJob("com.example.DummyJob", data);
        } finally {
            server.stop(0);
        }
        assertTrue(body[0].contains("custom/image:1"));
    }

    @Test
    public void testResourceLimits() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        final String[] body = new String[1];
        server.createContext("/apis/batch/v1/namespaces/test/jobs", ex -> {
            body[0] = new String(ex.getRequestBody().readAllBytes());
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        try {
            KubeJobDispatcher dispatcher = new KubeJobDispatcher(false, url, "test");
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("cpu", "500m");
            data.put("memory", "256Mi");
            dispatcher.dispatchJob("com.example.DummyJob", data);
        } finally {
            server.stop(0);
        }
        assertTrue(body[0].contains("500m"));
        assertTrue(body[0].contains("256Mi"));
    }

    @Test
    public void testBackoffLimit() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        final String[] body = new String[1];
        server.createContext("/apis/batch/v1/namespaces/test/jobs", ex -> {
            body[0] = new String(ex.getRequestBody().readAllBytes());
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        try {
            KubeJobDispatcher dispatcher = new KubeJobDispatcher(false, url, "test");
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("backoffLimit", 3);
            dispatcher.dispatchJob("com.example.DummyJob", data);
        } finally {
            server.stop(0);
        }
        assertTrue(body[0].contains("backoffLimit") && body[0].contains("3"));
    }

    @Test
    public void testDispatchCronJob() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        java.util.List<String> paths = new java.util.ArrayList<>();
        server.createContext("/apis/batch/v1/namespaces/test/cronjobs", ex -> {
            paths.add(ex.getRequestURI().getPath());
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        try {
            KubeJobDispatcher dispatcher = new KubeJobDispatcher(false, url, "test");
            dispatcher.dispatchCronJob("com.example.DummyJob", "*/5 * * * *", null);
        } finally {
            server.stop(0);
        }
        assertEquals(1, paths.size());
    }

    @Test
    public void testTemplateTtl() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img", 120, null, null, "ns");
        String yaml = builder.buildTemplate("com.example.DummyJob");
        assertTrue(yaml.contains("ttlSecondsAfterFinished: 120"));
        assertTrue(yaml.contains("namespace: ns"));

        String cronYaml = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null);
        assertTrue(cronYaml.contains("ttlSecondsAfterFinished: 120"));
        assertTrue(cronYaml.contains("namespace: ns"));
    }

    @Test
    public void testTemplateResources() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img", null, "250m", "128Mi", "ns");
        String yaml = builder.buildTemplate("com.example.DummyJob", null, null, null, null);
        assertTrue(yaml.contains("cpu: 250m"));
        assertTrue(yaml.contains("memory: 128Mi"));

        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null, null);
        assertTrue(cron.contains("cpu: 250m"));
        assertTrue(cron.contains("memory: 128Mi"));
        assertTrue(cron.contains("namespace: ns"));
    }

    @Test
    public void testTemplateBackoff() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img", null, null, null, "ns", 5);
        String yaml = builder.buildTemplate("com.example.DummyJob", null, null, null, null, null);
        assertTrue(yaml.contains("backoffLimit: 5"));

        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null, 4, null);
        assertTrue(cron.contains("backoffLimit: 4"));
    }

    @Test
    public void testTemplateSecurityContext() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img", null, null, null, "ns", null, 1000, 2000, 3000, null, null);
        String yaml = builder.buildTemplate("com.example.DummyJob", null, null, null, null, null);
        assertTrue(yaml.contains("securityContext"));
        assertTrue(yaml.contains("runAsUser: 1000"));
        assertTrue(yaml.contains("runAsGroup: 2000"));
        assertTrue(yaml.contains("fsGroup: 3000"));

        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null, null, null);
        assertTrue(cron.contains("runAsUser: 1000"));
        assertTrue(cron.contains("runAsGroup: 2000"));
        assertTrue(cron.contains("fsGroup: 3000"));
    }

    @Test
    public void testTemplateCustomEnv() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img");
        java.util.Map<String, String> env = new java.util.HashMap<>();
        env.put("FOO", "bar");
        env.put("BAZ", "qux");
        String yaml = builder.buildTemplate("com.example.DummyJob", null, null, null, null, env);
        assertTrue(yaml.contains("name: FOO"));
        assertTrue(yaml.contains("value: \"bar\""));

        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null, null, env);
        assertTrue(cron.contains("name: BAZ"));
        assertTrue(cron.contains("value: \"qux\""));
    }

    @Test
    public void testCronJobTimeZone() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img");
        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null, null, null, "UTC", null, null, null, null);
        assertTrue(cron.contains("timeZone: \"UTC\""));
    }

    @Test
    public void testTemplateLabelsAnnotations() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img");
        java.util.Map<String, String> labels = new java.util.HashMap<>();
        labels.put("app", "demo");
        java.util.Map<String, String> ann = new java.util.HashMap<>();
        ann.put("team", "qa");
        String yaml = builder.buildTemplate("com.example.DummyJob", null, null, null, null, null, labels, ann, null, null);
        assertTrue(yaml.contains("labels:"));
        assertTrue(yaml.contains("app: \"demo\""));
        assertTrue(yaml.contains("annotations:"));
        assertTrue(yaml.contains("team: \"qa\""));

        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null, null, null, "UTC", labels, ann, null, null);
        assertTrue(cron.contains("labels:"));
        assertTrue(cron.contains("annotations:"));
    }

    @Test
    public void testTemplateAffinity() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img");
        String affinity = "affinity:\n  podAffinity:\n    requiredDuringSchedulingIgnoredDuringExecution:\n    - labelSelector:\n        matchLabels:\n          app: demo\n      topologyKey: kubernetes.io/hostname";
        String yaml = builder.buildTemplate("com.example.DummyJob", null, null, null, null, null, null, null, affinity, null);
        assertTrue(yaml.contains("podAffinity"));

        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null, null, null, null, null, null, affinity, null);
        assertTrue(cron.contains("podAffinity"));
    }

    @Test
    public void testExternalTemplateFile() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("tpl", ".yaml");
        String template = "kind: Job\nmetadata:\n  name: ${JOB_NAME}\n  namespace: ${NAMESPACE}\nspec:\n  template:\n    spec:\n      containers:\n      - name: job\n        image: ${IMAGE}\n        env:\n        - name: JOB_CLASS\n          value: ${JOB_CLASS}\n";
        java.nio.file.Files.writeString(tmp, template);
        JobTemplateBuilder builder = new JobTemplateBuilder("img");
        String yaml = builder.buildTemplateFromFile("com.example.DummyJob", tmp.toString(), null, null, null, null, null, null, null, null, null);
        assertTrue(yaml.contains("name: com.example.dummyjob"));
        assertTrue(yaml.contains("namespace: default"));
        assertTrue(yaml.contains("image: img"));
        assertTrue(yaml.contains("value: \"com.example.DummyJob\""));
    }

    @Test
    public void testConcurrencyLimit() throws Exception {
        KubeJobDispatcher dispatcher = new KubeJobDispatcher(true, "", "default", 1);
        Runnable call = () -> dispatcher.dispatchJob(SlowLocalJob.class.getName());
        long start = System.currentTimeMillis();
        Thread t1 = new Thread(call);
        Thread t2 = new Thread(call);
        t1.start();
        Thread.sleep(10);
        t2.start();
        t1.join();
        t2.join();
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 400);
    }

    @Test
    public void testJobResultListener() {
        KubeJobDispatcher dispatcher = new KubeJobDispatcher(true);
        java.util.List<Boolean> results = new java.util.ArrayList<>();
        dispatcher.addListener((cls, success) -> results.add(success));
        LocalJob.count = 0;
        dispatcher.dispatchJob(LocalJob.class.getName());
        assertEquals(1, results.size());
        assertTrue(results.get(0));
    }

    @Test
    public void testCustomLogHandler() throws Exception {
        System.setProperty("STREAM_LOGS", "true");
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        String path = "/api/v1/namespaces/test/pods/" + LoggingJob.class.getName().toLowerCase() + "/log";
        server.createContext(path, ex -> {
            ex.sendResponseHeaders(200, 0);
            ex.getResponseBody().write("line1\nline2\n".getBytes());
            ex.close();
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        java.util.List<String> lines = new java.util.ArrayList<>();
        try {
            KubeJobDispatcher dispatcher = new KubeJobDispatcher(true, url, "test");
            dispatcher.setLogHandler((cls, line) -> lines.add(line));
            dispatcher.dispatchJob(LoggingJob.class.getName());
        } finally {
            server.stop(0);
            System.setProperty("STREAM_LOGS", "false");
        }
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("line1"));
    }
}
