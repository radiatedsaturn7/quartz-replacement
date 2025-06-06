package com.quartzkube.core;

/**
 * Responsible for creating Kubernetes Job manifests.
 * In normal mode it posts the generated YAML to the Kubernetes API. In
 * localMode it simply runs the job in-process.
 */
public class KubeJobDispatcher {
    private final JobTemplateBuilder templateBuilder;
    private final boolean localMode;
    private final String apiUrl;
    private final String namespace;
    private final java.util.concurrent.Semaphore dispatchLimiter;
    private final java.util.List<JobResultListener> listeners = new java.util.ArrayList<>();

    private static String getConfig(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isEmpty()) {
            v = System.getenv(key);
        }
        return v == null || v.isEmpty() ? def : v;
    }

    public KubeJobDispatcher() {
        this(
            Boolean.parseBoolean(getConfig("LOCAL_MODE", "false")),
            getConfig("KUBE_API_URL", "http://localhost:8001"),
            getConfig("JOB_NAMESPACE", "default"),
            parseLimit(getConfig("MAX_CONCURRENT_DISPATCHES", null))
        );
    }

    public KubeJobDispatcher(boolean localMode) {
        this(
            localMode,
            getConfig("KUBE_API_URL", "http://localhost:8001"),
            getConfig("JOB_NAMESPACE", "default"),
            parseLimit(getConfig("MAX_CONCURRENT_DISPATCHES", null))
        );
    }

    public KubeJobDispatcher(boolean localMode, String apiUrl, String namespace) {
        this(localMode, apiUrl, namespace, 0);
    }

    public KubeJobDispatcher(boolean localMode, String apiUrl, String namespace, int limit) {
        this.localMode = localMode;
        this.apiUrl = apiUrl;
        this.namespace = namespace;
        this.templateBuilder = new JobTemplateBuilder(
            getConfig("JOB_IMAGE", "quartz-job-runner:latest"),
            parseInt(getConfig("JOB_TTL_SECONDS", null)),
            emptyToNull(getConfig("CPU_LIMIT", null)),
            emptyToNull(getConfig("MEMORY_LIMIT", null)),
            namespace,
            parseInt(getConfig("JOB_BACKOFF_LIMIT", null)),
            parseInt(getConfig("RUN_AS_USER", null)),
            parseInt(getConfig("RUN_AS_GROUP", null)),
            parseInt(getConfig("FS_GROUP", null)),
            getConfig("CRON_TIME_ZONE", null)
        );
        int l = limit <= 0 ? Integer.MAX_VALUE : limit;
        this.dispatchLimiter = new java.util.concurrent.Semaphore(l);
    }

    /** Register a listener for job completion events. */
    public void addListener(JobResultListener l) {
        listeners.add(l);
    }

    private static int parseLimit(String v) {
        if (v == null || v.isEmpty()) return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Integer parseInt(String v) {
        if (v == null || v.isEmpty()) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    /**
     * Dispatch a CronJob with the given schedule. The schedule must be a
     * Kubernetes cron string. When running in local mode this simply runs the
     * job immediately.
     */
    public void dispatchCronJob(String jobClass, String schedule, java.util.Map<String, Object> jobData) {
        if (localMode) {
            dispatchJob(jobClass, jobData);
            return;
        }

        try {
            dispatchLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        String imageOverride = null;
        String cpuOverride = null;
        String memOverride = null;
        Integer backoffOverride = null;
        String templateFile = null;
        String affinity = null;
        String timeZoneOverride = null;
        java.util.Map<String, String> env = null;
        java.util.Map<String, String> labels = null;
        java.util.Map<String, String> annotations = null;
        if (jobData != null) {
            if (jobData.containsKey("k8sImage")) {
                Object v = jobData.get("k8sImage");
                if (v != null) {
                    imageOverride = v.toString();
                }
            }
            if (jobData.containsKey("cpu")) {
                Object v = jobData.get("cpu");
                if (v != null) {
                    cpuOverride = v.toString();
                }
            }
            if (jobData.containsKey("memory")) {
                Object v = jobData.get("memory");
                if (v != null) {
                    memOverride = v.toString();
                }
            }
            if (jobData.containsKey("backoffLimit")) {
                Object v = jobData.get("backoffLimit");
                if (v != null) {
                    try {
                        backoffOverride = Integer.parseInt(v.toString());
                    } catch (NumberFormatException ignored) { }
                }
            }
            if (jobData.containsKey("env")) {
                Object obj = jobData.get("env");
                if (obj instanceof java.util.Map<?, ?> m) {
                    env = new java.util.HashMap<>();
                    for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                        env.put(e.getKey().toString(), e.getValue().toString());
                    }
                }
            }
            if (jobData.containsKey("labels")) {
                Object obj = jobData.get("labels");
                if (obj instanceof java.util.Map<?, ?> m) {
                    labels = new java.util.HashMap<>();
                    for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                        labels.put(e.getKey().toString(), e.getValue().toString());
                    }
                }
            }
            if (jobData.containsKey("annotations")) {
                Object obj = jobData.get("annotations");
                if (obj instanceof java.util.Map<?, ?> m) {
                    annotations = new java.util.HashMap<>();
                    for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                        annotations.put(e.getKey().toString(), e.getValue().toString());
                    }
                }
            }
            if (jobData.containsKey("timeZone")) {
                Object v = jobData.get("timeZone");
                if (v != null) {
                    timeZoneOverride = v.toString();
                }
            }
            if (jobData.containsKey("cronTemplateFile")) {
                Object v = jobData.get("cronTemplateFile");
                if (v != null) {
                    templateFile = v.toString();
                }
            }
            if (jobData.containsKey("affinity")) {
                Object v = jobData.get("affinity");
                if (v != null) {
                    affinity = v.toString();
                }
            }
        }

        String manifest;
        if (templateFile != null) {
            manifest = templateBuilder.buildCronJobTemplateFromFile(jobClass, schedule, templateFile, imageOverride, cpuOverride, memOverride, backoffOverride, env, timeZoneOverride, labels, annotations, affinity);
        } else {
            manifest = templateBuilder.buildCronJobTemplate(jobClass, schedule, imageOverride, cpuOverride, memOverride, backoffOverride, env, timeZoneOverride, labels, annotations, affinity);
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.URI uri = java.net.URI.create(
                apiUrl + "/apis/batch/v1/namespaces/" + namespace + "/cronjobs");
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/yaml")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(manifest))
                .build();
            java.net.http.HttpResponse<String> resp = client.send(
                request,
                java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            System.out.println("Submitted cronjob, response code: " + resp.statusCode());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                Metrics.getInstance().recordSuccess();
            } else {
                Metrics.getInstance().recordFailure();
            }
        } catch (Exception e) {
            Metrics.getInstance().recordFailure();
            e.printStackTrace();
        }
        streamLogs(jobClass);
        dispatchLimiter.release();
    }

    /**
     * Builds a Job manifest for the provided job class and outputs it. In a
     * full implementation this would POST to the Kubernetes API.
     */
    public void dispatchJob(String jobClass) {
        dispatchJob(jobClass, null);
    }

    /**
     * Dispatch a job with optional job data (e.g., image override).
     */
    public void dispatchJob(String jobClass, java.util.Map<String, Object> jobData) {
        if (localMode) {
            boolean success = false;
            try {
                dispatchLimiter.acquire();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                Class<?> clazz = Class.forName(jobClass);
                Runnable job = (Runnable) clazz.getDeclaredConstructor().newInstance();
                job.run();
                Metrics.getInstance().recordSuccess();
                success = true;
            } catch (Exception e) {
                Metrics.getInstance().recordFailure();
                e.printStackTrace();
            } finally {
                streamLogs(jobClass);
                notifyResult(jobClass, success);
                dispatchLimiter.release();
            }
            return;
        }

        try {
            dispatchLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        String imageOverride = null;
        String cpuOverride = null;
        String memOverride = null;
        Integer backoffOverride = null;
        String templateFile = null;
        String affinity = null;
        java.util.Map<String, String> env = null;
        java.util.Map<String, String> labels = null;
        java.util.Map<String, String> annotations = null;
        if (jobData != null) {
            if (jobData.containsKey("k8sImage")) {
                Object v = jobData.get("k8sImage");
                if (v != null) {
                    imageOverride = v.toString();
                }
            }
            if (jobData.containsKey("cpu")) {
                Object v = jobData.get("cpu");
                if (v != null) {
                    cpuOverride = v.toString();
                }
            }
            if (jobData.containsKey("memory")) {
                Object v = jobData.get("memory");
                if (v != null) {
                    memOverride = v.toString();
                }
            }
            if (jobData.containsKey("backoffLimit")) {
                Object v = jobData.get("backoffLimit");
                if (v != null) {
                    try {
                        backoffOverride = Integer.parseInt(v.toString());
                    } catch (NumberFormatException ignored) { }
                }
            }
            if (jobData.containsKey("env")) {
                Object obj = jobData.get("env");
                if (obj instanceof java.util.Map<?, ?> m) {
                    env = new java.util.HashMap<>();
                    for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                        env.put(e.getKey().toString(), e.getValue().toString());
                    }
                }
            }
            if (jobData.containsKey("labels")) {
                Object obj = jobData.get("labels");
                if (obj instanceof java.util.Map<?, ?> m) {
                    labels = new java.util.HashMap<>();
                    for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                        labels.put(e.getKey().toString(), e.getValue().toString());
                    }
                }
            }
            if (jobData.containsKey("annotations")) {
                Object obj = jobData.get("annotations");
                if (obj instanceof java.util.Map<?, ?> m) {
                    annotations = new java.util.HashMap<>();
                    for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                        annotations.put(e.getKey().toString(), e.getValue().toString());
                    }
                }
            }
            if (jobData.containsKey("templateFile")) {
                Object v = jobData.get("templateFile");
                if (v != null) {
                    templateFile = v.toString();
                }
            }
            if (jobData.containsKey("affinity")) {
                Object v = jobData.get("affinity");
                if (v != null) {
                    affinity = v.toString();
                }
            }
        }

        String manifest;
        if (templateFile != null) {
            manifest = templateBuilder.buildTemplateFromFile(jobClass, templateFile, imageOverride, cpuOverride, memOverride, backoffOverride, env, labels, annotations, affinity);
        } else {
            manifest = templateBuilder.buildTemplate(jobClass, imageOverride, cpuOverride, memOverride, backoffOverride, env, labels, annotations, affinity);
        }
        boolean success = false;
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.URI uri = java.net.URI.create(
                apiUrl + "/apis/batch/v1/namespaces/" + namespace + "/jobs");
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/yaml")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(manifest))
                .build();
            java.net.http.HttpResponse<String> resp = client.send(
                request,
                java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            System.out.println("Submitted job, response code: " + resp.statusCode());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                Metrics.getInstance().recordSuccess();
                success = true;
            } else {
                Metrics.getInstance().recordFailure();
            }
        } catch (Exception e) {
            Metrics.getInstance().recordFailure();
            e.printStackTrace();
        }
        streamLogs(jobClass);
        notifyResult(jobClass, success);
        dispatchLimiter.release();
    }

    /**
     * Stream logs for the pod created by the given job class. This makes a
     * simple GET request to the Kubernetes API and prints the response body.
     */
    private void streamLogs(String jobClass) {
        String podName = jobClass.toLowerCase();
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.URI uri = java.net.URI.create(
                apiUrl + "/api/v1/namespaces/" + namespace + "/pods/" + podName + "/log?follow=false");
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri).GET().build();
            java.net.http.HttpResponse<java.io.InputStream> resp = client.send(
                request,
                java.net.http.HttpResponse.BodyHandlers.ofInputStream()
            );
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(resp.body()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to stream logs: " + e.getMessage());
        }
    }

    private void notifyResult(String jobClass, boolean success) {
        for (JobResultListener l : listeners) {
            try {
                l.jobFinished(jobClass, success);
            } catch (Exception ignore) {}
        }
    }
}
