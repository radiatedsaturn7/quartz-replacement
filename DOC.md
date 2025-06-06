# QuartzKube User Guide

QuartzKube is a lightweight drop-in replacement for the Quartz Scheduler. Instead of running jobs in the local JVM it launches them as Kubernetes Jobs or CronJobs. This guide walks you through migration and common configuration.

## 1. Getting Started

1. **Add the library** – build this project and include the resulting JAR in your application.
2. **Build a job image** – use the provided `Dockerfile` to create a container containing your job classes. Push it to a registry accessible by your cluster.
3. **Configure cluster access** – make sure the scheduler can reach the Kubernetes API. Review [docs/cluster-setup.md](docs/cluster-setup.md) for the minimal RBAC rules.

## 2. Basic Usage

Create a `QuartzKubeScheduler` and schedule jobs just like with Quartz:

```java
QuartzKubeScheduler scheduler = new QuartzKubeScheduler();
scheduler.start();
JobDetail detail = JobBuilder.newJob(MyJob.class).withIdentity("id").build();
Trigger trig = TriggerBuilder.newTrigger().startNow().build();
scheduler.scheduleJob(detail, trig);
```

Jobs must implement `Runnable` with a public no-arg constructor.

## 3. Cron Jobs

Cron schedules are supported via `dispatchCronJob` on `KubeJobDispatcher`:

```java
Map<String,Object> data = new HashMap<>();
data.put("timeZone", "UTC"); // optional
scheduler.getDispatcher().dispatchCronJob(MyJob.class.getName(), "*/5 * * * *", data);
```

The optional `timeZone` entry sets the Kubernetes `timeZone` field. You can also specify a global default using the `CRON_TIME_ZONE` environment variable.

## 4. Migration from Quartz

Quartz job classes can be reused without modification. Replace the Quartz `Scheduler` with `QuartzKubeScheduler` and remove any quartz-specific configuration. For example:

```java
// Quartz code
Scheduler sched = StdSchedulerFactory.getDefaultScheduler();
JobDetail detail = JobBuilder.newJob(MyJob.class).withIdentity("id").build();
Trigger trig = TriggerBuilder.newTrigger().startNow().build();
sched.start();
sched.scheduleJob(detail, trig);

// QuartzKube
QuartzKubeScheduler sched = new QuartzKubeScheduler();
sched.start();
sched.scheduleJob(detail, trig); // JobDetail and Trigger reused
```

Any custom Kubernetes options (image, resources, etc.) are provided through a `Map` when dispatching a job or cron job.

## 5. Useful Environment Variables

- `JOB_IMAGE` – default container image for jobs (`quartz-job-runner:latest`)
- `JOB_NAMESPACE` – target namespace (default `default`)
- `CPU_LIMIT` / `MEMORY_LIMIT` – resource limits for pods
- `JOB_BACKOFF_LIMIT` – Kubernetes job restart attempts
- `JOB_TTL_SECONDS` – TTL after job completion
- `RUN_AS_USER`, `RUN_AS_GROUP`, `FS_GROUP` – pod security context
- `MAX_CONCURRENT_DISPATCHES` – limit concurrent job submissions
- `CRON_TIME_ZONE` – default time zone for CronJobs

Metrics are exposed via JMX under the object name `com.quartzkube.core:type=Metrics`.
Set `METRICS_PORT` to expose an HTTP `/metrics` endpoint in Prometheus format.

## 6. Advanced Features

- **Custom templates** – pass `templateFile` (Job) or `cronTemplateFile` (CronJob) in the data map to render your own YAML.
- **Persistence** – implement `JobStore` (e.g. `CrdJobStore`) and pass it to `QuartzKubeScheduler` to keep scheduled jobs across restarts.

- **Job result listeners** – register a `JobResultListener` with `KubeJobDispatcher` to be notified when jobs finish.
- **Custom labels/annotations** – include `labels` or `annotations` maps in job data to tag created resources.
- **Pod affinity/anti-affinity** – supply an `affinity` YAML snippet in the job data to set `spec.affinity` rules.
