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
    private final Integer runAsUser;
    private final Integer runAsGroup;
    private final Integer fsGroup;
    private final String cronTimeZone;
    private final String serviceAccount;

    private static String readFile(String path) throws java.io.IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of(path));
    }

    private String renderTemplate(String template,
                                  String jobClass,
                                  String schedule,
                                  String image,
                                  String cpu,
                                  String memory,
                                  Integer backoff,
                                  String timeZone,
                                  java.util.Map<String, String> extraEnv,
                                  java.util.Map<String, String> labels,
                                  java.util.Map<String, String> annotations,
                                  String affinity,
                                  String serviceAccount,
                                  String extraContainers,
                                  String volumes) {
        String envLines = "- name: JOB_CLASS\n          value: \"" + jobClass + "\"";
        if (extraEnv != null) {
            for (java.util.Map.Entry<String, String> e : extraEnv.entrySet()) {
                envLines += "\n        - name: " + e.getKey() + "\n          value: \"" + e.getValue() + "\"";
            }
        }

        String labelLines = "";
        if (labels != null && !labels.isEmpty()) {
            labelLines = "  labels:\n";
            for (java.util.Map.Entry<String, String> e : labels.entrySet()) {
                labelLines += "    " + e.getKey() + ": \"" + e.getValue() + "\"\n";
            }
        }

        String annotationLines = "";
        if (annotations != null && !annotations.isEmpty()) {
            annotationLines = "  annotations:\n";
            for (java.util.Map.Entry<String, String> e : annotations.entrySet()) {
                annotationLines += "    " + e.getKey() + ": \"" + e.getValue() + "\"\n";
            }
        }

        template = template.replace("${JOB_CLASS}", "\"" + jobClass + "\"")
                .replace("${JOB_NAME}", jobClass.toLowerCase())
                .replace("${NAMESPACE}", namespace)
                .replace("${IMAGE}", image)
                .replace("${ENV}", envLines)
                .replace("${LABELS}", labelLines)
                .replace("${ANNOTATIONS}", annotationLines)
                .replace("${AFFINITY}", affinity != null ? affinity : "")
                .replace("${SERVICE_ACCOUNT}", serviceAccount != null ? serviceAccount : "")
                .replace("${EXTRA_CONTAINERS}", extraContainers != null ? extraContainers : "")
                .replace("${VOLUMES}", volumes != null ? volumes : "");

        if (schedule != null) template = template.replace("${SCHEDULE}", schedule);
        if (timeZone != null) template = template.replace("${TIME_ZONE}", timeZone);
        if (cpu != null) template = template.replace("${CPU_LIMIT}", cpu);
        if (memory != null) template = template.replace("${MEMORY_LIMIT}", memory);
        Integer bo = backoff != null ? backoff : backoffLimit;
        if (bo != null) template = template.replace("${BACKOFF_LIMIT}", bo.toString());
        if (ttlSeconds != null) template = template.replace("${TTL_SECONDS}", ttlSeconds.toString());
        if (runAsUser != null) template = template.replace("${RUN_AS_USER}", runAsUser.toString());
        if (runAsGroup != null) template = template.replace("${RUN_AS_GROUP}", runAsGroup.toString());
        if (fsGroup != null) template = template.replace("${FS_GROUP}", fsGroup.toString());
        return template;
    }

    public JobTemplateBuilder() {
        String envImage = getConfig("JOB_IMAGE");
        if (envImage == null || envImage.isEmpty()) {
            envImage = "quartz-job-runner:latest";
        }
        this.image = envImage;

        Integer ttl = null;
        String ttlEnv = getConfig("JOB_TTL_SECONDS");
        if (ttlEnv != null && !ttlEnv.isEmpty()) {
            try {
                ttl = Integer.parseInt(ttlEnv);
            } catch (NumberFormatException e) {
                ttl = null;
            }
        }
        this.ttlSeconds = ttl;

        String cpu = getConfig("CPU_LIMIT");
        String mem = getConfig("MEMORY_LIMIT");
        this.cpuLimit = cpu != null && !cpu.isEmpty() ? cpu : null;
        this.memoryLimit = mem != null && !mem.isEmpty() ? mem : null;
        String ns = getConfig("JOB_NAMESPACE");
        if (ns == null || ns.isEmpty()) ns = "default";
        this.namespace = ns;

        Integer backoff = null;
        String backoffEnv = getConfig("JOB_BACKOFF_LIMIT");
        if (backoffEnv != null && !backoffEnv.isEmpty()) {
            try {
                backoff = Integer.parseInt(backoffEnv);
            } catch (NumberFormatException e) {
                backoff = null;
            }
        }
        this.backoffLimit = backoff;

        this.runAsUser = parseInt(getConfig("RUN_AS_USER"));
        this.runAsGroup = parseInt(getConfig("RUN_AS_GROUP"));
        this.fsGroup = parseInt(getConfig("FS_GROUP"));
        String tzEnv = getConfig("CRON_TIME_ZONE");
        this.cronTimeZone = tzEnv != null && !tzEnv.isEmpty() ? tzEnv : null;
        String saEnv = getConfig("SERVICE_ACCOUNT");
        this.serviceAccount = saEnv != null && !saEnv.isEmpty() ? saEnv : null;
    }

    public JobTemplateBuilder(String image) {
        this(image, null);
    }

    private static String getConfig(String key) {
        String v = System.getProperty(key);
        if (v == null || v.isEmpty()) {
            v = System.getenv(key);
        }
        return v;
    }

    private static String getEnvOrDefault(String key, String def) {
        String v = getConfig(key);
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

    private static String defaultNamespace() {
        String ns = getConfig("JOB_NAMESPACE");
        return (ns == null || ns.isEmpty()) ? "default" : ns;
    }


    public JobTemplateBuilder(String image, Integer ttlSeconds) {
        this(image, ttlSeconds, null, null);
    }

    public JobTemplateBuilder(String image, Integer ttlSeconds, String cpuLimit, String memoryLimit) {
        this(image, ttlSeconds, cpuLimit, memoryLimit, defaultNamespace(), null, null, null, null, null, null);
    }

    public JobTemplateBuilder(String image, Integer ttlSeconds, String cpuLimit, String memoryLimit, String namespace) {
        this(image, ttlSeconds, cpuLimit, memoryLimit, namespace, null, null, null, null, null, null);
    }

    public JobTemplateBuilder(String image, Integer ttlSeconds, String cpuLimit, String memoryLimit, String namespace, Integer backoffLimit) {
        this(image, ttlSeconds, cpuLimit, memoryLimit, namespace, backoffLimit, null, null, null, null, null);
    }

    public JobTemplateBuilder(String image, Integer ttlSeconds, String cpuLimit, String memoryLimit, String namespace, Integer backoffLimit,
                              Integer runAsUser, Integer runAsGroup, Integer fsGroup, String cronTimeZone, String serviceAccount) {
        this.image = image;
        this.ttlSeconds = ttlSeconds;
        this.cpuLimit = cpuLimit;
        this.memoryLimit = memoryLimit;
        this.namespace = namespace;
        this.backoffLimit = backoffLimit;
        this.runAsUser = runAsUser;
        this.runAsGroup = runAsGroup;
        this.fsGroup = fsGroup;
        this.cronTimeZone = cronTimeZone;
        this.serviceAccount = serviceAccount;
    }

    /**
     * Builds a Job manifest from an external YAML template file using simple variable substitution.
     * Supported variables include ${JOB_CLASS}, ${JOB_NAME}, ${NAMESPACE}, ${IMAGE}, ${ENV},
     * ${CPU_LIMIT}, ${MEMORY_LIMIT}, ${BACKOFF_LIMIT}, ${TTL_SECONDS}, ${RUN_AS_USER},
     * ${RUN_AS_GROUP}, ${FS_GROUP}, ${LABELS}, ${ANNOTATIONS}.
     */
    public String buildTemplateFromFile(String jobClass, String templateFile, String imageOverride,
                                        String cpuOverride, String memoryOverride, Integer backoffOverride,
                                        java.util.Map<String, String> extraEnv,
                                        java.util.Map<String, String> labels,
                                        java.util.Map<String, String> annotations,
                                        String affinity,
                                        String serviceAccountOverride,
                                        String extraContainers,
                                        String volumes) {
        try {
            String template = readFile(templateFile);
            String img = imageOverride != null ? imageOverride : image;
            String cpu = cpuOverride != null ? cpuOverride : cpuLimit;
            String mem = memoryOverride != null ? memoryOverride : memoryLimit;
            String sa = serviceAccountOverride != null ? serviceAccountOverride : serviceAccount;
            return renderTemplate(template, jobClass, null, img, cpu, mem, backoffOverride, null,
                    extraEnv, labels, annotations, affinity, sa, extraContainers, volumes);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read template file", e);
        }
    }

    /**
     * Builds a CronJob manifest from an external YAML template file.
     * The template may use the same variables as {@link #buildTemplateFromFile} plus ${SCHEDULE}.
     */
    public String buildCronJobTemplateFromFile(String jobClass, String schedule, String templateFile, String imageOverride,
                                               String cpuOverride, String memoryOverride, Integer backoffOverride,
                                               java.util.Map<String, String> extraEnv, String timeZoneOverride,
                                               java.util.Map<String, String> labels,
                                               java.util.Map<String, String> annotations,
                                               String affinity,
                                               String serviceAccountOverride,
                                               String extraContainers,
                                               String volumes) {
        try {
            String template = readFile(templateFile);
            String img = imageOverride != null ? imageOverride : image;
            String cpu = cpuOverride != null ? cpuOverride : cpuLimit;
            String mem = memoryOverride != null ? memoryOverride : memoryLimit;
            String tz = timeZoneOverride != null ? timeZoneOverride : cronTimeZone;
            String sa = serviceAccountOverride != null ? serviceAccountOverride : serviceAccount;
            template = renderTemplate(template, jobClass, schedule, img, cpu, mem, backoffOverride, tz,
                    extraEnv, labels, annotations, affinity, sa, extraContainers, volumes);
            return template;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read template file", e);
        }
    }

    /**
     * Builds a very basic Kubernetes Job YAML manifest for the given job class.
     * This does not apply advanced settings but is enough for testing purposes.
     */
    public String buildTemplate(String jobClass) {
        return buildTemplate(jobClass, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Builds a basic Kubernetes Job YAML manifest with an optional image override.
     */
    public String buildTemplate(String jobClass, String imageOverride) {
        return buildTemplate(jobClass, imageOverride, null, null, null, null, null, null, null, null);
    }

    public String buildTemplate(String jobClass, String imageOverride, String cpuOverride, String memoryOverride) {
        return buildTemplate(jobClass, imageOverride, cpuOverride, memoryOverride, null, null, null, null, null, null);
    }

    public String buildTemplate(String jobClass, String imageOverride, String cpuOverride, String memoryOverride, Integer backoffOverride) {
        return buildTemplate(jobClass, imageOverride, cpuOverride, memoryOverride, backoffOverride, null, null, null, null, null);
    }

    public String buildTemplate(String jobClass, String imageOverride, String cpuOverride, String memoryOverride,
                               Integer backoffOverride, java.util.Map<String, String> extraEnv) {
        return buildTemplate(jobClass, imageOverride, cpuOverride, memoryOverride, backoffOverride, extraEnv, null, null, null, null);
    }

    public String buildTemplate(String jobClass, String imageOverride, String cpuOverride, String memoryOverride,
                               Integer backoffOverride, java.util.Map<String, String> extraEnv,
                               java.util.Map<String, String> labels, java.util.Map<String, String> annotations,
                               String affinity, String serviceAccountOverride) {
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
        String securityLines = "";
        if (runAsUser != null || runAsGroup != null || fsGroup != null) {
            securityLines = "      securityContext:\n";
            if (runAsUser != null) {
                securityLines += "        runAsUser: " + runAsUser + "\n";
            }
            if (runAsGroup != null) {
                securityLines += "        runAsGroup: " + runAsGroup + "\n";
            }
            if (fsGroup != null) {
                securityLines += "        fsGroup: " + fsGroup + "\n";
            }
        }

        String saLine = "";
        if (serviceAccount != null && !serviceAccount.isEmpty()) {
            saLine = "      serviceAccountName: " + serviceAccount + "\n";
        }

        String affinityLines = "";
        if (affinity != null && !affinity.isEmpty()) {
            for (String line : affinity.split("\\r?\\n")) {
                affinityLines += "      " + line + "\n";
            }
        }

        String envLines = "        - name: JOB_CLASS\n          value: \"" + jobClass + "\"";
        if (extraEnv != null) {
            for (java.util.Map.Entry<String, String> e : extraEnv.entrySet()) {
                envLines += "\n        - name: " + e.getKey() + "\n          value: \"" + e.getValue() + "\"";
            }
        }

        String labelLines = "";
        if (labels != null && !labels.isEmpty()) {
            labelLines = "  labels:\n";
            for (java.util.Map.Entry<String, String> e : labels.entrySet()) {
                labelLines += "    " + e.getKey() + ": \"" + e.getValue() + "\"\n";
            }
        }

        String annotationLines = "";
        if (annotations != null && !annotations.isEmpty()) {
            annotationLines = "  annotations:\n";
            for (java.util.Map.Entry<String, String> e : annotations.entrySet()) {
                annotationLines += "    " + e.getKey() + ": \"" + e.getValue() + "\"\n";
            }
        }

        return String.format("""
apiVersion: batch/v1
kind: Job
metadata:
  name: %s
  namespace: %s
%s%s%s  template:
    spec:
      restartPolicy: Never
%s%s%s      containers:
      - name: job
        image: %s
%s        env:
%s
""", jobName, namespace, labelLines, annotationLines, backoffLine + ttlLine, securityLines, saLine, affinityLines, img, resourceLines, envLines);
    }

    /**
     * Builds a simple Kubernetes CronJob manifest for the given job class and
     * schedule. The schedule must be a standard cron string accepted by
     * Kubernetes (minute-level granularity).
     */
    public String buildCronJobTemplate(String jobClass, String schedule, String imageOverride) {
        return buildCronJobTemplate(jobClass, schedule, imageOverride, null, null, null, null, null, null, null, null, null);
    }

    public String buildCronJobTemplate(String jobClass, String schedule, String imageOverride, String cpuOverride, String memoryOverride) {
        return buildCronJobTemplate(jobClass, schedule, imageOverride, cpuOverride, memoryOverride, null, null, null, null, null, null, null);
    }

    public String buildCronJobTemplate(String jobClass, String schedule, String imageOverride, String cpuOverride, String memoryOverride, Integer backoffOverride) {
        return buildCronJobTemplate(jobClass, schedule, imageOverride, cpuOverride, memoryOverride, backoffOverride, null, null, null, null, null, null);
    }

    public String buildCronJobTemplate(String jobClass, String schedule, String imageOverride, String cpuOverride, String memoryOverride, Integer backoffOverride, java.util.Map<String, String> extraEnv) {
        return buildCronJobTemplate(jobClass, schedule, imageOverride, cpuOverride, memoryOverride, backoffOverride, extraEnv, null, null, null, null, null);
    }

    public String buildCronJobTemplate(String jobClass, String schedule, String imageOverride, String cpuOverride,
                                       String memoryOverride, Integer backoffOverride, java.util.Map<String, String> extraEnv,
                                       String timeZoneOverride, java.util.Map<String, String> labels,
                                       java.util.Map<String, String> annotations,
                                       String affinity, String serviceAccountOverride) {
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
        String securityLines = "";
        if (runAsUser != null || runAsGroup != null || fsGroup != null) {
            securityLines = "          securityContext:\n";
            if (runAsUser != null) {
                securityLines += "            runAsUser: " + runAsUser + "\n";
            }
            if (runAsGroup != null) {
                securityLines += "            runAsGroup: " + runAsGroup + "\n";
            }
            if (fsGroup != null) {
                securityLines += "            fsGroup: " + fsGroup + "\n";
            }
        }

        String envLines = "            - name: JOB_CLASS\n              value: \"" + jobClass + "\"";
        if (extraEnv != null) {
            for (java.util.Map.Entry<String, String> e : extraEnv.entrySet()) {
                envLines += "\n            - name: " + e.getKey() + "\n              value: \"" + e.getValue() + "\"";
            }
        }

        String labelLines = "";
        if (labels != null && !labels.isEmpty()) {
            labelLines = "  labels:\n";
            for (java.util.Map.Entry<String, String> e : labels.entrySet()) {
                labelLines += "    " + e.getKey() + ": \"" + e.getValue() + "\"\n";
            }
        }

        String annotationLines = "";
        if (annotations != null && !annotations.isEmpty()) {
            annotationLines = "  annotations:\n";
            for (java.util.Map.Entry<String, String> e : annotations.entrySet()) {
                annotationLines += "    " + e.getKey() + ": \"" + e.getValue() + "\"\n";
            }
        }

        String affinityLines = "";
        if (affinity != null && !affinity.isEmpty()) {
            for (String line : affinity.split("\\r?\\n")) {
                affinityLines += "          " + line + "\n";
            }
        }

        String tz = timeZoneOverride != null ? timeZoneOverride : cronTimeZone;
        StringBuilder sb = new StringBuilder();
        sb.append("apiVersion: batch/v1\n");
        sb.append("kind: CronJob\n");
        sb.append("metadata:\n");
        sb.append("  name: ").append(jobName).append("-cron\n");
        sb.append("  namespace: ").append(namespace).append("\n");
        sb.append("spec:\n");
        sb.append("  schedule: \"").append(schedule).append("\"\n");
        if (tz != null) sb.append("  timeZone: \"").append(tz).append("\"\n");
        if (!labelLines.isEmpty()) sb.append(labelLines);
        if (!annotationLines.isEmpty()) sb.append(annotationLines);
        sb.append("  jobTemplate:\n");
        sb.append("    spec:\n");
        if (!backoffLine.isEmpty()) sb.append(backoffLine);
        if (!ttlLine.isEmpty()) sb.append(ttlLine);
        sb.append("      template:\n");
        sb.append("        spec:\n");
        sb.append("          restartPolicy: Never\n");
        String sa = serviceAccountOverride != null ? serviceAccountOverride : serviceAccount;
        if (sa != null && !sa.isEmpty()) sb.append("          serviceAccountName: " + sa + "\n");
        if (!securityLines.isEmpty()) sb.append(securityLines);
        if (!affinityLines.isEmpty()) sb.append(affinityLines);
        sb.append("          containers:\n");
        sb.append("          - name: job\n");
        sb.append("            image: ").append(img).append("\n");
        if (!resourceLines.isEmpty()) sb.append(resourceLines);
        sb.append("            env:\n");
        sb.append(envLines);
        return sb.toString();
    }
}
