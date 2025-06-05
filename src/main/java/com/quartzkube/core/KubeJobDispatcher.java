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

    public KubeJobDispatcher() {
        this(
            Boolean.parseBoolean(System.getenv("LOCAL_MODE")),
            System.getenv().getOrDefault("KUBE_API_URL", "http://localhost:8001"),
            System.getenv().getOrDefault("JOB_NAMESPACE", "default"),
            parseLimit(System.getenv("MAX_CONCURRENT_DISPATCHES"))
        );
    }

    public KubeJobDispatcher(boolean localMode) {
        this(
            localMode,
            System.getenv().getOrDefault("KUBE_API_URL", "http://localhost:8001"),
            System.getenv().getOrDefault("JOB_NAMESPACE", "default"),
            parseLimit(System.getenv("MAX_CONCURRENT_DISPATCHES"))
        );
    }

    public KubeJobDispatcher(boolean localMode, String apiUrl, String namespace) {
        this(localMode, apiUrl, namespace, 0);
    }

    public KubeJobDispatcher(boolean localMode, String apiUrl, String namespace, int limit) {
        this.localMode = localMode;
        this.apiUrl = apiUrl;
        this.namespace = namespace;
        this.templateBuilder = new JobTemplateBuilder(namespace);
        int l = limit <= 0 ? Integer.MAX_VALUE : limit;
        this.dispatchLimiter = new java.util.concurrent.Semaphore(l);
    }

    private static int parseLimit(String v) {
        if (v == null || v.isEmpty()) return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
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
        JobMetrics.recordDispatch();

        String imageOverride = null;
        String cpuOverride = null;
        String memOverride = null;
        Integer backoffOverride = null;
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
        }

        String manifest = templateBuilder.buildCronJobTemplate(jobClass, schedule, imageOverride, cpuOverride, memOverride, backoffOverride);
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
        } catch (Exception e) {
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
            try {
                dispatchLimiter.acquire();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                JobMetrics.recordDispatch();
                Class<?> clazz = Class.forName(jobClass);
                Runnable job = (Runnable) clazz.getDeclaredConstructor().newInstance();
                try {
                    job.run();
                    JobMetrics.recordSuccess();
                } catch (Exception e) {
                    JobMetrics.recordFailure();
                    throw e;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                streamLogs(jobClass);
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
        JobMetrics.recordDispatch();
        String imageOverride = null;
        String cpuOverride = null;
        String memOverride = null;
        Integer backoffOverride = null;
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
        }

        String manifest = templateBuilder.buildTemplate(jobClass, imageOverride, cpuOverride, memOverride, backoffOverride);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        streamLogs(jobClass);
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
}
