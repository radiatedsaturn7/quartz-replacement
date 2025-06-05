package com.quartzkube.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class KubeJobDispatcherTest {
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

    @Test
    public void testLocalDispatchRunsJob() {
        KubeJobDispatcher dispatcher = new KubeJobDispatcher(true);
        dispatcher.dispatchJob(LocalJob.class.getName());
        assertEquals(1, LocalJob.count);
    }

    @Test
    public void testLogStreaming() throws Exception {
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
        assertTrue(body[0].contains("image: custom/image:1"));
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
        assertTrue(body[0].contains("cpu: 500m"));
        assertTrue(body[0].contains("memory: 256Mi"));
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
        assertTrue(body[0].contains("backoffLimit: 3"));
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
        String yaml = builder.buildTemplate("com.example.DummyJob", null, null, null);
        assertTrue(yaml.contains("cpu: 250m"));
        assertTrue(yaml.contains("memory: 128Mi"));

        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null);
        assertTrue(cron.contains("cpu: 250m"));
        assertTrue(cron.contains("memory: 128Mi"));
        assertTrue(cron.contains("namespace: ns"));
    }

    @Test
    public void testTemplateBackoff() {
        JobTemplateBuilder builder = new JobTemplateBuilder("img", null, null, null, "ns", 5);
        String yaml = builder.buildTemplate("com.example.DummyJob", null, null, null, null);
        assertTrue(yaml.contains("backoffLimit: 5"));

        String cron = builder.buildCronJobTemplate("com.example.DummyJob", "*/5 * * * *", null, null, null, 4);
        assertTrue(cron.contains("backoffLimit: 4"));
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
    public void testMetricsCount() {
        JobMetrics.reset();
        KubeJobDispatcher dispatcher = new KubeJobDispatcher(true);
        dispatcher.dispatchJob(LocalJob.class.getName());
        assertEquals(1, JobMetrics.getDispatchedCount());
        assertEquals(1, JobMetrics.getSucceededCount());
        assertEquals(0, JobMetrics.getFailedCount());
    }
}
