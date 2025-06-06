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

`scheduleJob` accepts `CronTrigger` or `SimpleTrigger` instances. Cron triggers
run repeatedly according to their cron expression, while simple triggers
schedule the job using the provided start time and repeat interval.

### 2.1 Scheduler Factory

`QuartzKubeSchedulerFactory` creates schedulers using environment variables,
system properties or a `quartzkube.properties` file. Set `HOT_RELOAD=true` to
watch the file for changes.

```java
QuartzKubeScheduler scheduler = new QuartzKubeSchedulerFactory().getScheduler();
```

Jobs must implement `Runnable` **or** `org.quartz.Job` with a public no-arg constructor.

## 3. Cron Jobs

Cron schedules are supported via `dispatchCronJob` on `KubeJobDispatcher`:

```java
Map<String,Object> data = new HashMap<>();
data.put("timeZone", "UTC"); // optional
scheduler.getDispatcher().dispatchCronJob(MyJob.class.getName(), "*/5 * * * *", data);
```

The optional `timeZone` entry sets the Kubernetes `timeZone` field. You can also specify a global default using the `CRON_TIME_ZONE` environment variable.

## 4. Migration from Quartz

Quartz job classes can be reused without modification. To migrate:

1. Replace `StdSchedulerFactory.getDefaultScheduler()` (or similar) with `new QuartzKubeScheduler()`.
2. Start the scheduler and invoke `scheduleJob` with your existing `JobDetail` and `Trigger` objects.

No other code changes are required. For example:

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

## 7. More Migration Examples

Switching from Quartz is straightforward because existing `JobDetail` and `Trigger` objects work unchanged. The most common adjustments involve Kubernetes-specific options. The examples below build on the standard Quartz code.

### 7.1 Override the Container Image

Provide an image in the job data map to run a different container per job:

```java
JobDataMap map = new JobDataMap();
map.put("k8sImage", "myregistry/my-job:1.0");
scheduler.scheduleJob(detail, trig, map);
```

### 7.2 Run Jobs Locally for Testing

Set the `LOCAL_MODE` environment variable to `true` so jobs execute in-process without contacting Kubernetes:

```bash
export LOCAL_MODE=true
```

This lets you test your job code with the same scheduler API.

### 7.3 Using a Custom Namespace
### 7.4 Helm Example

A ready-to-use Helm chart resides in `charts/hello-world`. Install it after building the project:

```bash
helm install my-job ./charts/hello-world
```

Then follow the job logs:

```bash
kubectl logs job/my-job -f
```


Override the namespace when constructing the dispatcher:

```java
KubeJobDispatcher dispatcher = new KubeJobDispatcher(false, "https://my-cluster", "testing");
QuartzKubeScheduler scheduler = new QuartzKubeScheduler(dispatcher);
```

## 8. Troubleshooting

- **Pods never start** – verify your RBAC rules allow creating Jobs and Pods. See `docs/cluster-setup.md` for the required permissions.
- **Image pull errors** – ensure the job image is accessible from the cluster and any private registry credentials are configured as a Kubernetes secret.
- **Metrics not exposed** – check that the `METRICS_PORT` environment variable is set and that no firewall blocks access to the chosen port.
- **Properties file changes ignored** – if using `QuartzKubeSchedulerFactory`, set `HOT_RELOAD=true` to enable automatic reloading.
