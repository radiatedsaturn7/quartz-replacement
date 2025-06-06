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
    private final KubernetesApiService apiService;
    private final boolean useWatch;
    private final boolean streamLogs;
    private final java.util.concurrent.Semaphore dispatchLimiter;
    private final java.util.List<JobResultListener> listeners = new java.util.ArrayList<>();
    private PodLogHandler logHandler = new StdoutLogHandler();

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
        this.useWatch = Boolean.parseBoolean(getConfig("USE_WATCH", "true"));
        this.streamLogs = Boolean.parseBoolean(getConfig("STREAM_LOGS", "true"));
        String impl = getConfig("K8S_CLIENT_IMPL", "fabric8");
        if ("official".equalsIgnoreCase(impl)) {
            io.kubernetes.client.openapi.ApiClient client;
            try {
                client = io.kubernetes.client.util.ClientBuilder.standard()
                        .setBasePath(apiUrl)
                        .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            client.setVerifyingSsl(false);
            this.apiService = new OfficialKubernetesApiService(client, namespace);
        } else {
            io.fabric8.kubernetes.client.Config cfg = new io.fabric8.kubernetes.client.ConfigBuilder()
                    .withMasterUrl(apiUrl)
                    .withNamespace(namespace)
                    .withTrustCerts(true)
                    .build();
            io.fabric8.kubernetes.client.KubernetesClient client =
                    new io.fabric8.kubernetes.client.DefaultKubernetesClient(cfg);
            this.apiService = new Fabric8KubernetesApiService(client, namespace);
        }
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
            getConfig("CRON_TIME_ZONE", null),
            getConfig("SERVICE_ACCOUNT", null)
        );
        int l = limit <= 0 ? Integer.MAX_VALUE : limit;
        this.dispatchLimiter = new java.util.concurrent.Semaphore(l);
    }

    /** Register a listener for job completion events. */
    public void addListener(JobResultListener l) {
        listeners.add(l);
    }

    /** Set a custom handler to process pod log lines. */
    public void setLogHandler(PodLogHandler handler) {
        if (handler != null) {
            this.logHandler = handler;
        }
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
        String extraContainers = null;
        String volumes = null;
        java.util.Map<String, String> env = null;
        java.util.Map<String, String> labels = null;
        java.util.Map<String, String> annotations = null;
        String saOverride = null;
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
            if (jobData.containsKey("serviceAccount")) {
                Object v = jobData.get("serviceAccount");
                if (v != null) {
                    saOverride = v.toString();
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
            if (jobData.containsKey("extraContainers")) {
                Object v = jobData.get("extraContainers");
                if (v != null) {
                    extraContainers = v.toString();
                }
            }
            if (jobData.containsKey("volumes")) {
                Object v = jobData.get("volumes");
                if (v != null) {
                    volumes = v.toString();
                }
            }
            if (jobData.containsKey("extraContainers")) {
                Object v = jobData.get("extraContainers");
                if (v != null) {
                    extraContainers = v.toString();
                }
            }
            if (jobData.containsKey("volumes")) {
                Object v = jobData.get("volumes");
                if (v != null) {
                    volumes = v.toString();
                }
            }
        }

        String manifest;
        if (templateFile != null) {
            manifest = templateBuilder.buildCronJobTemplateFromFile(jobClass, schedule, templateFile, imageOverride, cpuOverride, memOverride, backoffOverride, env, timeZoneOverride, labels, annotations, affinity, saOverride, extraContainers, volumes);
        } else {
            manifest = templateBuilder.buildCronJobTemplate(jobClass, schedule, imageOverride, cpuOverride, memOverride, backoffOverride, env, timeZoneOverride, labels, annotations, affinity, saOverride);
        }
        try {
            apiService.create(manifest);
            Metrics.getInstance().recordSuccess();
        } catch (Exception e) {
            Metrics.getInstance().recordFailure();
            e.printStackTrace();
        }
        streamLogs(jobClass);
        monitorJob(jobClass);
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
        String saOverride = null;
        String extraContainers = null;
        String volumes = null;
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
            if (jobData.containsKey("serviceAccount")) {
                Object v = jobData.get("serviceAccount");
                if (v != null) {
                    saOverride = v.toString();
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
            manifest = templateBuilder.buildTemplateFromFile(jobClass, templateFile, imageOverride, cpuOverride, memOverride, backoffOverride, env, labels, annotations, affinity, saOverride, extraContainers, volumes);
        } else {
            manifest = templateBuilder.buildTemplate(jobClass, imageOverride, cpuOverride, memOverride, backoffOverride, env, labels, annotations, affinity, saOverride);
        }
        boolean submitted = false;
        try {
            apiService.create(manifest);
            Metrics.getInstance().recordSuccess();
            submitted = true;
        } catch (Exception e) {
            Metrics.getInstance().recordFailure();
            e.printStackTrace();
        }
        if (submitted) {
            streamLogs(jobClass);
            monitorJob(jobClass);
        } else {
            notifyResult(jobClass, false);
        }
        dispatchLimiter.release();
    }

    /**
     * Stream logs for the pod created by the given job class. This makes a
     * simple GET request to the Kubernetes API and prints the response body.
     */
    private void streamLogs(String jobClass) {
        if (!streamLogs) {
            return;
        }
        String podName = jobClass.toLowerCase();
        try {
            String logs = apiService.readPodLog(podName);
            for (String line : logs.split("\r?\n")) {
                if (!line.isEmpty()) logHandler.handle(jobClass, line);
            }
        } catch (Exception e) {
            logHandler.handle(jobClass, "Failed to stream logs: " + e.getMessage());
        }
    }

    /** Watch the pod for completion and notify listeners. */
    private void monitorJob(String jobClass) {
        if (!useWatch || localMode) {
            return;
        }
        String podName = jobClass.toLowerCase();
        Thread t = new Thread(() -> {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final io.fabric8.kubernetes.client.Watch watch = apiService.watchPod(
                    podName,
                    new io.fabric8.kubernetes.client.Watcher<io.fabric8.kubernetes.api.model.Pod>() {
                        @Override
                        public void eventReceived(Action action, io.fabric8.kubernetes.api.model.Pod pod) {
                            String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : null;
                            if ("Succeeded".equals(phase)) {
                                notifyResult(jobClass, true);
                                latch.countDown();
                            } else if ("Failed".equals(phase)) {
                                notifyResult(jobClass, false);
                                latch.countDown();
                            }
                        }

                        @Override
                        public void onClose(io.fabric8.kubernetes.client.WatcherException e) {
                            latch.countDown();
                        }
                    });
            try {
                latch.await();
            } catch (InterruptedException ignored) {}
            watch.close();
        });
        t.setDaemon(true);
        t.start();
    }

    private void notifyResult(String jobClass, boolean success) {
        for (JobResultListener l : listeners) {
            try {
                l.jobFinished(jobClass, success);
            } catch (Exception ignore) {}
        }
    }
}
