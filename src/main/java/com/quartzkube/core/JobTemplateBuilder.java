package com.quartzkube.core;

/**
 * Builds Kubernetes job templates from job information.
 */
public class JobTemplateBuilder {
    private final String image;
    private final Integer ttlSeconds;
    private final String cpuLimit;
    private final String memoryLimit;
    private final String namespace;
    private final Integer backoffLimit;

    public JobTemplateBuilder() {
        String envImage = System.getenv("JOB_IMAGE");
        if (envImage == null || envImage.isEmpty()) {
            envImage = "quartz-job-runner:latest";
        }
        this.image = envImage;

        Integer ttl = null;
        String ttlEnv = System.getenv("JOB_TTL_SECONDS");
        if (ttlEnv != null && !ttlEnv.isEmpty()) {
            try {
                ttl = Integer.parseInt(ttlEnv);
            } catch (NumberFormatException e) {
                ttl = null;
            }
        }
        this.ttlSeconds = ttl;

        String cpu = System.getenv("CPU_LIMIT");
        String mem = System.getenv("MEMORY_LIMIT");
        this.cpuLimit = cpu != null && !cpu.isEmpty() ? cpu : null;
        this.memoryLimit = mem != null && !mem.isEmpty() ? mem : null;
        String ns = System.getenv().getOrDefault("JOB_NAMESPACE", "default");
        this.namespace = ns;

        Integer backoff = null;
        String backoffEnv = System.getenv("JOB_BACKOFF_LIMIT");
        if (backoffEnv != null && !backoffEnv.isEmpty()) {
            try {
                backoff = Integer.parseInt(backoffEnv);
            } catch (NumberFormatException e) {
                backoff = null;
            }
        }
        this.backoffLimit = backoff;
    }

    public JobTemplateBuilder(String image) {
        this(image, null);
    }

    private static String getEnvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? def : v;
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

    public JobTemplateBuilder(String namespace) {
        this(
            getEnvOrDefault("JOB_IMAGE", "quartz-job-runner:latest"),
            parseInt(System.getenv("JOB_TTL_SECONDS")),
            emptyToNull(System.getenv("CPU_LIMIT")),
            emptyToNull(System.getenv("MEMORY_LIMIT")),
            namespace,
            parseInt(System.getenv("JOB_BACKOFF_LIMIT"))
        );
    }

    public JobTemplateBuilder(String image, Integer ttlSeconds) {
        this(image, ttlSeconds, null, null);
    }

    public JobTemplateBuilder(String image, Integer ttlSeconds, String cpuLimit, String memoryLimit) {
        this(image, ttlSeconds, cpuLimit, memoryLimit, System.getenv().getOrDefault("JOB_NAMESPACE", "default"), null);
    }

    public JobTemplateBuilder(String image, Integer ttlSeconds, String cpuLimit, String memoryLimit, String namespace) {
        this(image, ttlSeconds, cpuLimit, memoryLimit, namespace, null);
    }

    public JobTemplateBuilder(String image, Integer ttlSeconds, String cpuLimit, String memoryLimit, String namespace, Integer backoffLimit) {
        this.image = image;
        this.ttlSeconds = ttlSeconds;
        this.cpuLimit = cpuLimit;
        this.memoryLimit = memoryLimit;
        this.namespace = namespace;
        this.backoffLimit = backoffLimit;
    }

    /**
     * Builds a very basic Kubernetes Job YAML manifest for the given job class.
     * This does not apply advanced settings but is enough for testing purposes.
     */
    public String buildTemplate(String jobClass) {
        return buildTemplate(jobClass, null, null, null, null);
    }

    /**
     * Builds a basic Kubernetes Job YAML manifest with an optional image override.
     */
    public String buildTemplate(String jobClass, String imageOverride) {
        return buildTemplate(jobClass, imageOverride, null, null, null);
    }

    public String buildTemplate(String jobClass, String imageOverride, String cpuOverride, String memoryOverride) {
        return buildTemplate(jobClass, imageOverride, cpuOverride, memoryOverride, null);
    }

    public String buildTemplate(String jobClass, String imageOverride, String cpuOverride, String memoryOverride, Integer backoffOverride) {
        String jobName = jobClass.toLowerCase();
        String img = imageOverride != null ? imageOverride : image;
        String ttlLine = "";
        if (ttlSeconds != null) {
            ttlLine = "  ttlSecondsAfterFinished: " + ttlSeconds + "\n";
        }
        String backoffLine = "";
        Integer bo = backoffOverride != null ? backoffOverride : backoffLimit;
        if (bo != null) {
            backoffLine = "  backoffLimit: " + bo + "\n";
        }
        String cpu = cpuOverride != null ? cpuOverride : cpuLimit;
        String mem = memoryOverride != null ? memoryOverride : memoryLimit;
        String resourceLines = "";
        if (cpu != null || mem != null) {
            resourceLines = "        resources:\n          limits:\n";
            if (cpu != null) {
                resourceLines += "            cpu: " + cpu + "\n";
            }
            if (mem != null) {
                resourceLines += "            memory: " + mem + "\n";
            }
        }
        return String.format("""
apiVersion: batch/v1
kind: Job
metadata:
  name: %s
  namespace: %s
spec:
%s%s  template:
    spec:
      restartPolicy: Never
      containers:
      - name: job
        image: %s
%s        env:
        - name: JOB_CLASS
          value: "%s"
""", jobName, namespace, backoffLine, ttlLine, img, resourceLines, jobClass);
    }

    /**
     * Builds a simple Kubernetes CronJob manifest for the given job class and
     * schedule. The schedule must be a standard cron string accepted by
     * Kubernetes (minute-level granularity).
     */
    public String buildCronJobTemplate(String jobClass, String schedule, String imageOverride) {
        return buildCronJobTemplate(jobClass, schedule, imageOverride, null, null, null);
    }

    public String buildCronJobTemplate(String jobClass, String schedule, String imageOverride, String cpuOverride, String memoryOverride) {
        return buildCronJobTemplate(jobClass, schedule, imageOverride, cpuOverride, memoryOverride, null);
    }

    public String buildCronJobTemplate(String jobClass, String schedule, String imageOverride, String cpuOverride, String memoryOverride, Integer backoffOverride) {
        String jobName = jobClass.toLowerCase();
        String img = imageOverride != null ? imageOverride : image;
        String ttlLine = "";
        if (ttlSeconds != null) {
            ttlLine = "      ttlSecondsAfterFinished: " + ttlSeconds + "\n";
        }
        String backoffLine = "";
        Integer bo = backoffOverride != null ? backoffOverride : backoffLimit;
        if (bo != null) {
            backoffLine = "    backoffLimit: " + bo + "\n";
        }
        String cpu = cpuOverride != null ? cpuOverride : cpuLimit;
        String mem = memoryOverride != null ? memoryOverride : memoryLimit;
        String resourceLines = "";
        if (cpu != null || mem != null) {
            resourceLines = "            resources:\n              limits:\n";
            if (cpu != null) {
                resourceLines += "                cpu: " + cpu + "\n";
            }
            if (mem != null) {
                resourceLines += "                memory: " + mem + "\n";
            }
        }
        return String.format("""
apiVersion: batch/v1
kind: CronJob
metadata:
  name: %s-cron
  namespace: %s
spec:
  schedule: "%s"
  jobTemplate:
    spec:
%s%s      template:
        spec:
          restartPolicy: Never
          containers:
          - name: job
            image: %s
%s            env:
            - name: JOB_CLASS
              value: "%s"
""", jobName, namespace, schedule, backoffLine, ttlLine, img, resourceLines, jobClass);
    }
}
