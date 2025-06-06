🧠 Project: QuartzKube – A Kubernetes-backed Quartz-Compatible Scheduler
🎯 Goal
Create a drop-in replacement for Quartz Scheduler that maintains full API compatibility but instead of executing jobs locally, schedules and dispatches them to Kubernetes Pods (containers) for execution.

⚙️ Key Requirements
Quartz API Compatibility

Same class names, interfaces (Job, JobDetail, Trigger, Scheduler, etc.)

Behaves identically from the caller’s perspective.

Backwards-compatible with existing Quartz job definitions.

Kubernetes-Based Execution

Each scheduled job spawns a Pod in a target namespace.

Configurable image, command, environment, secrets, etc.

Job metadata (name, group, trigger info) passed as environment variables or via mounted config.

Persistence Layer (Optional)

Store scheduled job definitions in etcd, PostgreSQL, or something simple.

Optional: Leverage Kubernetes CRDs for storing jobs instead.

Failure Handling

Retry policy: integrate with Kubernetes Job/Pod retry mechanics.

Exponential backoff, max attempts, etc.

Notify developer of pod failures via logs/events or a callback hook.

Security and Resource Limits

Allow job constraints like CPU/memory limits.

Namespace isolation.

Support for RBAC service accounts per job group.

📦 Architecture Overview
Application Code
     ↓
QuartzKube Scheduler (Quartz API shim)
     ↓
Job Dispatcher → Kubernetes API
     ↓
Creates Kubernetes Job (or Pod)
     ↓
Container runs Java job class
🔧 Component Breakdown
1. QuartzKubeScheduler
Implements org.quartz.Scheduler.

Intercepts scheduleJob(...), start(), pauseJob(), etc.

Handles scheduling logic using internal JobStore.

2. KubeJobDispatcher
Handles Kubernetes API calls (Java K8S client or HTTP).

Translates JobDetail and Trigger into a Kubernetes Job manifest.

Injects job metadata into the pod as env vars or a config volume.

Supports image pull secrets, custom command args, and volume mounts.

3. JobRunner Base Image
A base container image that:

Reads env vars or config map for job class name.

Uses reflection to load and execute job class (which must be on the classpath).

Reports job success/failure.

4. JobTemplateBuilder
Converts job and trigger info into Kubernetes YAML.

Optional: support templates via Helm/Kustomize for customization.

5. JobResultListener (Optional)
Interface to notify application about job results, errors, logs, etc.

Async pub/sub or callback.

🔁 Sample Flow
JobDetail job = JobBuilder.newJob(MyJob.class)
    .withIdentity("emailJob", "group1")
    .build();

Trigger trigger = TriggerBuilder.newTrigger()
    .withSchedule(CronScheduleBuilder.cronSchedule("0 0/5 * * * ?"))
    .build();

scheduler.scheduleJob(job, trigger);
What Happens Under the Hood:

scheduleJob intercepted by QuartzKubeScheduler.

Stores job + trigger in internal store (in-memory or persistent).

At trigger time, KubeJobDispatcher fires.

It creates a Pod or Job in K8S, with:

MY_JOB_CLASS=MyJob

Image = quartz-runner:latest

CPU/Mem limits, etc.

Pod spins up, reflects on MyJob, runs it, reports status.

🧪 Testing Strategy
Mirror Quartz test suite.

Mock K8S API for unit testing.

Integration tests using KinD or Minikube.

Use real containers with test Job classes.

🚀 Extensibility
Support custom backends: AWS Batch, GCP Cloud Run, etc.

Add support for shared queues (e.g., Kafka + workers).

Integration with Prometheus/Grafana for monitoring job executions.

🔐 Security Considerations
Secure K8S API access (RBAC, limited service accounts).

No arbitrary command execution—whitelist images or job classes.

Pod security policies or OPA/Gatekeeper.

📂 Example Folder Structure
quartzkube/
├── core/
│   └── QuartzKubeScheduler.java
│   └── KubeJobDispatcher.java
│   └── JobTemplateBuilder.java
├── runner/
│   └── Dockerfile
│   └── JobRunner.java
├── examples/
│   └── HelloWorldJob.java
├── test/
│   └── SchedulerIntegrationTest.java
🛠 Codex Implementation Plan
Scaffold QuartzKubeScheduler with dummy methods to match the interface.

Build internal JobStore (start with in-memory, expand later).

Write KubeJobDispatcher using Fabric8 K8s client.

Create JobRunner base image in Docker.

Build E2E test that schedules a simple job via K8S.

Want me to write out the stub classes and first scaffold code for Codex to chew on next? Or you want to wire this yourself and just have me back you up when you hit a snag?

You said:
No, can you write up the complete thing? I want to drop the design doc into a git repository, and then have Codex implement the stupid thing. Like I can't believe I'm the dude that needs to fix the internet, but here we are.
ChatGPT said:
Got it. Before I write the full design doc for Codex to implement your Quartz-compatible Kubernetes job runner, could you confirm a few things so I tailor it right?

What image should be used for the job containers (e.g., a specific Java base image)?

Do you want support for both one-shot and recurring jobs (like CRON jobs)?

Should logs be captured and made available via Kubernetes Logs, or exported elsewhere (e.g., to a database or log aggregator)?

Will all jobs be Java-based and contain their own class implementing Job, or should the container pull code dynamically?

What Kubernetes client (e.g., Fabric8, official Java client) would you prefer?

Once I’ve got this, I’ll spit out the full doc—ready to drop straight into Git.

You said:
1. I think that should be a configuration.
2. Yes
3. Whatever is most compatible with Quartz. Ideally out to the console, but someone running a stupid quartz thing might want to have the logs streamed or something.
4. I don't know, whatever java developers do and want to do.
5. I don't give a shit, whatever keeps the internet happy.
ChatGPT said:
Perfect. I’ll write a full-blown design doc for a Java library that mirrors the Quartz Scheduler API but dispatches job execution to Kubernetes containers. The doc will be structured, annotated, and optimized for direct use in a Git repository so Codex can take over implementation without tripping over itself. I’ll drop it here when it’s ready.

Research completed in 5m · 16 sources · 63 searches
Quartz-Compatible Kubernetes Job Scheduler Design Doc

Quartz-Kubernetes Scheduler Design Document
Introduction
Quartz is a popular open-source job scheduling library for Java that allows applications to schedule and run jobs (tasks) at specified times or intervals. The goal of this project is to create a Java library that is API-compatible with Quartz (implementing the same key interfaces such as Scheduler, Job, JobDetail, Trigger, etc.
quartz-scheduler.org
) while offloading the execution of jobs to a Kubernetes cluster. Instead of running scheduled jobs on the local JVM (threads), each job execution will be dispatched to run in a Kubernetes Pod (or a Kubernetes Job which manages Pods). This allows leveraging Kubernetes for resource management, isolation, scaling, and reliability of job executions. By preserving the Quartz API, Java developers can use this library as a drop-in replacement for Quartz with minimal changes to their code. They can define jobs and triggers in the same way, but the actual work will run in containers on Kubernetes. This document outlines the design of the library, including its architecture, how it interfaces with Kubernetes, how it remains compatible with Quartz, and configuration and extensibility considerations.
Goals and Requirements
Quartz API Compatibility: The library will implement the core Quartz Scheduler interfaces (Scheduler, Job, JobDetail, Trigger, etc.) so that existing code using Quartz can switch to this implementation without significant changes
quartz-scheduler.org
. The scheduling DSL (e.g. using JobBuilder, TriggerBuilder) and listener mechanisms should behave similarly to Quartz where possible. The Scheduler provided by this library should accept jobs and triggers and manage their lifecycle analogous to a standard Quartz scheduler.
Kubernetes Pod Execution: Instead of executing jobs on local threads, each job firing is executed in a Kubernetes cluster as a separate Pod (encapsulated in a Kubernetes Job resource for reliability). The library will create a Kubernetes Job/Pod for each execution of a JobDetail when a trigger fires. This means the actual execute() method of the Quartz Job runs inside a container on the cluster, not in the original JVM process.
Support One-shot and Recurring Jobs: Both one-time jobs (with a specific scheduled time or run immediately) and recurring jobs (with simple intervals or CRON expressions) must be supported, similar to Quartz’s support for one-shot triggers and cron triggers. Cron-based scheduling should support Quartz cron expressions (which include seconds) for fine-grained scheduling, as well as simple repeated intervals.
Configurable Job Container Image: The Docker image used for running the job code in Kubernetes should be configurable. Developers might provide a default image that contains their job classes and all necessary dependencies. The library should allow specifying this image via configuration (and possibly override per job if needed). The container image must have access to the job’s compiled classes (see "Job Execution Environment" below).
Logging to Stdout/Stderr with Streaming Option: Jobs running in Kubernetes will output logs to the container’s stdout/stderr. This output should be accessible to developers. The design will ensure logs from the job’s container are available, ideally in a console-friendly format. An optional feature will be to stream these logs back to the application that scheduled the job (in real-time or near-real-time) so that from a developer’s perspective, it feels similar to seeing job logs in the local console. This might be achieved by attaching to Pod logs via the Kubernetes API. If not streaming, the logs can at least be fetched on completion or viewed via standard kubectl logs mechanisms.
Job Class Accessibility in Container: The implementation must address how the job’s Java class (which implements org.quartz.Job) is made available inside the container that runs the job. A common pattern is assumed: for example, the container image might include the application’s JAR or classes, so that the job class can be loaded and executed. We will design a Job Runner mechanism that the container uses to locate and run the specified Job class. This could involve passing the job’s class name and data to the container as environment variables or command-line arguments.
Use of Kubernetes Java Client: Interactions with the Kubernetes API should use a popular Java Kubernetes client library. We may use the official Kubernetes Java client or the Fabric8 Kubernetes Client. Fabric8 has been a widely-used Kubernetes client for Java (one of the most popular since 2015)
nubenetes.com
, offering a fluent DSL and resource watchers, whereas the official client is maintained by Kubernetes contributors and aligns closely with k8s API conventions. The design will favor using one of these (with a lean toward Fabric8 for its simplicity) and abstract the client usage so it could be swapped or upgraded in the future.
Retry, Backoff, and Failure Handling: The system should leverage Kubernetes’ native features to handle job retries and failures. When creating Kubernetes Job resources for each execution, we can set parameters like .spec.backoffLimit (the number of retries before marking the job as failed, default 6 in Kubernetes
kubernetes.io
) and let Kubernetes perform exponential back-off between retries
kubernetes.io
. The library should surface failure information (e.g., if a job fails after all retries) back to the developer through the Quartz API where possible (perhaps via job listener callbacks or error logs). It should also consider using Kubernetes Job status to determine success/failure.
Persistent Job Store (Optional): In Quartz, a persistent job store (e.g. JDBC database) is used for storing job schedules and state, enabling recovery after restarts and clustering. This library design should allow optional persistence of job and trigger definitions, either by integrating with a database (similarly to Quartz’s JDBCJobStore) or by using Kubernetes custom resources (CRDs) to store the schedule. Using a database would allow multiple scheduler instances to coordinate (for HA/clustering) via locking, as Quartz does
quartz-scheduler.org
. Using Kubernetes CRDs would make the schedule declarative in the cluster (and could enable a future Kubernetes-native operator to handle scheduling). The design will include an interface for a Job Store and provide at least an in-memory implementation by default, with extension points for a JDBC store or CRD-based store.
Minimal Application Changes: The library should require minimal changes to existing Quartz-using applications. For example, a user might only need to initialize the scheduler via a different factory class (e.g., KubernetesSchedulerFactory instead of StdSchedulerFactory) or change some configuration, and then use the Scheduler as usual. All job definitions, trigger configurations, and listener registrations would remain the same.
Architecture Overview
The Quartz-Kubernetes Scheduler library consists of several components working together to achieve the above goals. At a high level, the architecture is split between the scheduling side (Java application) and the execution side (Kubernetes cluster):
 Java Application (Scheduler)                       Kubernetes Cluster (Execution)
 --------------------------------                   --------------------------------
 | Quartz-compatible API (Scheduler) |             | Kubernetes API Server         |
 |  - Stores JobDetail & Triggers    |             |  - Accepts Job/Pod specs      |
 |  - Determines next execution time |             |  - Schedules Pods on Nodes    |
 |  - Dispatches jobs to K8s        |             |                              |
 |                                  |             |  Pod (Job Execution)          |
 | Kubernetes Client (Fabric8/Official)|----->    |  - Runs JobRunner container    |
 |  - Connects to cluster API       |    API      |  - Executes Job class          |
 |  - Creates Job/Pod resources     |   calls     |  - Outputs logs to stdout      |
 |  - Monitors job status           |             |  - Reports status on finish    |
 |                                  |             |                              |
 | Optional Persistent Job Store    |             | Kubernetes Controllers         |
 |  (DB or CRD) for HA/Recovery     |             |  - Job Controller (handles retry/backoff) |
 --------------------------------                   --------------------------------
Figure: Architecture Overview – The scheduler library runs within the Java application and interacts with the Kubernetes cluster via the Kubernetes API. Each scheduled job execution results in a Kubernetes Job/Pod being created. Kubernetes runs the pod (in a suitable node), and the job’s Java code executes inside that pod. The library monitors the Kubernetes job status and updates its internal state accordingly. Key architectural components and their roles:
Quartz Compatibility Layer: This is the set of classes that implement Quartz interfaces (Scheduler, JobDetail, Trigger, etc.) and provide the same methods and behavior (as much as possible). For example, KubernetesScheduler might implement Quartz’s Scheduler interface. It will manage job scheduling, trigger registration, listeners, etc., but delegate execution to Kubernetes. It uses an internal scheduler loop or timer to track trigger firing times (similar to Quartz's scheduling threads).
Job Store: An interface/abstract component that manages storage of job and trigger metadata. The default implementation might be in-memory (non-persistent), suitable for simple deployments or development. For persistence, a JDBC-based store can be provided (storing jobs/triggers in a relational database similar to Quartz’s tables) so that jobs survive application restarts and multiple instances can coordinate. Another implementation could use Kubernetes CRDs (Custom Resource Definitions) to persist job schedules in the cluster itself. Custom resources are extensions of the Kubernetes API used to store domain-specific objects
kubernetes.io
, which in our case could represent scheduled jobs. For example, a ScheduledJob CRD might contain the cron expression, job class, and other details. This data would live in etcd (Kubernetes backing store) and be accessible even if the scheduler app restarts.
Kubernetes Dispatcher: This component encapsulates the Kubernetes client interactions. It is responsible for translating a Quartz job execution request into a Kubernetes Job (or Pod) object and using the Kubernetes API to create it in the cluster. It also watches or polls for the status of the job. The dispatcher uses the Java Kubernetes client (Fabric8 or official). Fabric8’s fluent API can simplify creating resource definitions in code. For example, creating a Job with Fabric8 might involve constructing a JobBuilder with metadata, specifying backoffLimit, etc., instead of writing raw YAML. The dispatcher abstracts these details away from the core scheduler logic.
Job Runner Container: This refers to the runtime environment inside the Kubernetes Pod that actually runs the Job.execute(...) method. The container image for the job runner is configurable. Typically, it will be an image that includes the application’s code or at least the classes necessary to run the job. One design is to include a JobRunner Java program in the image – this program’s responsibility is to accept parameters (like which job class to run, and job data) and then use reflection or a plugin system to instantiate the job class and call its execute() method. The job runner might be invoked by specifying the main class and arguments in the Pod spec. For example, the Kubernetes dispatcher could create a Job with a Pod template like:
apiVersion: batch/v1
kind: Job
metadata:
  name: quartz-job-myJob123
spec:
  backoffLimit: 6                # Retries before failure (default 6):contentReference[oaicite:7]{index=7}
  template:
    spec:
      restartPolicy: Never       # Don't restart within the same Pod; use new Pod for retries:contentReference[oaicite:8]{index=8}
      containers:
      - name: job-runner
        image: mycompany/jobs:1.0   # Configured job image containing the code
        args: ["com.myapp.JobRunner", "--jobClass", "com.myapp.MyJob", "--jobData", "{\"key\":\"value\"}"]
        env:
          - name: JOB_NAME
            value: "myJobInstanceId"
In the above, JobRunner is a Java class (part of our library or provided by the user) that knows how to read the --jobClass and --jobData arguments or environment variables, load that class (which is on the classpath in the container), and execute it. The restartPolicy: Never is used so that if the job fails, Kubernetes will treat the Pod as failed and, via the Job controller, create a new Pod if retries remain
kubernetes.io
. The backoffLimit: 6 means Kubernetes will try up to 6 retries by default
kubernetes.io
, doubling the wait time after each failure (10s, 20s, 40s... capped at 6 minutes between tries) for exponential backoff.
Logging and Monitoring: The library will also include components for log streaming and monitoring execution. When a Kubernetes Job/Pod is launched, the scheduler can attach to it to stream logs. This might be implemented via a separate thread using the Kubernetes client’s log API (for example, Fabric8 client can fetch logs or follow logs of a Pod). If enabled, this will forward the container’s stdout/stderr to the application’s console or logging framework. This makes the remote execution more transparent. Alternatively, if streaming is disabled, the user can rely on kubectl logs or cluster logging tools to view output. The library will also monitor the status of each job execution (perhaps by watching the Job resource or polling for its completion) so it knows when the execution is finished, and whether it succeeded or failed.
Below is a sequence diagram illustrating the interactions when scheduling and executing a job:
sequenceDiagram
    participant UserApp as User Application (Using Scheduler)
    participant Scheduler as K8sQuartzScheduler (Library)
    participant API as Kubernetes API Server
    participant Pod as Job Pod (Container)
    UserApp->>Scheduler: scheduleJob(JobDetail, Trigger)
    Scheduler->>Scheduler: Store job & trigger (JobStore)
    Scheduler->>Scheduler: Calculate next fire time for trigger
    UserApp-->Scheduler: (Scheduler runs in background after start())
    Note over Scheduler: Scheduler thread waits until trigger's fire time
    Scheduler->>API: Create Kubernetes Job (with Pod template for the job)
    API-->>Scheduler: Job accepted (API response)
    API-->>Pod: Launch Pod (pull image, start container)
    Pod->>Pod: Initialize JobRunner with job class & data
    Pod->>Pod: Execute job logic (Job.execute())
    Pod-->>API: Report completion (success or fail status)
    Scheduler->>API: (Optionally stream logs during execution)
    Scheduler->>API: Monitor Job status (poll or watch)
    API-->>Scheduler: Job status = Succeeded/Failed
    Scheduler->>Scheduler: Mark execution done; update internal state
    Scheduler->>Scheduler: For recurring trigger, compute next fire time
    Scheduler->>UserApp: (Optional) Notify listeners or handle result
Figure: Job Scheduling and Execution Flow – The application schedules a job via the Quartz-like API. The library stores the job and waits for the trigger to fire. When it's time, the library uses the Kubernetes API to create a Job/Pod to execute the job. The Pod runs the specified job class inside a container. The library optionally streams logs and monitors the job’s completion. Finally, it updates the schedule for the next run if the trigger is recurring.
Quartz Compatibility Layer Design
Scheduler Interface Implementation
At the heart of the library is the implementation of the org.quartz.Scheduler interface (or an equivalent interface if we avoid depending on Quartz artifacts directly). The Scheduler implementation (let's call it KubernetesScheduler) is responsible for managing job scheduling just like the Quartz StdScheduler. Key points in the design:
The KubernetesScheduler will have methods like scheduleJob(JobDetail job, Trigger trigger), pauseTrigger(TriggerKey), deleteJob(JobKey), etc., matching Quartz API. Internally, when a job is scheduled, the scheduler will store the job metadata (perhaps in an in-memory map or via the Job Store) and compute the next fire time based on the Trigger. It does not execute the job immediately in the local JVM. Instead, it will rely on a timing mechanism to know when the trigger's fire time is reached.
A scheduling loop or thread pool will be used to wait for triggers to fire. This could be implemented with a dedicated scheduler thread that sleeps until the next trigger time, or using a delay queue of triggers. (We could potentially reuse some Quartz code for trigger calendars and misfire handling if license allows, or implement our own simplified logic.)
Trigger types: We must support Simple triggers (one-shot or fixed interval repeats) and Cron triggers (based on cron expressions). The library can provide implementations of SimpleTrigger and CronTrigger that mirror Quartz’s. For example, a CronTrigger might hold a cron expression and use Quartz’s CronExpression parser or a similar utility to compute next fire times. Quartz cron expressions include seconds and other advanced features, which we will support. If using Quartz’s own classes (if the Quartz library is on the classpath), we could accept a org.quartz.CronTrigger and read its properties. If not, we can implement a parser.
JobDetail and Job: The JobDetail holds the job’s definition (job class, identity, durable or not, etc.) and a JobDataMap of data. We will likely use the same JobDetail and JobDataMap classes from Quartz or provide clones. The Job interface (with the execute(JobExecutionContext) method) will remain the same. Developers will implement this interface for their jobs. However, unlike standard Quartz, calling execute will happen in a remote container. The JobExecutionContext provided to the job might be a limited version since not all aspects make sense remotely (for example, a reference to the Scheduler or Trigger might not be fully available unless we serialize the necessary info).
Listeners and Hooks: Quartz allows job listeners, trigger listeners, and scheduler listeners to hook into events (job to be executed, job execution complete, trigger misfired, etc.). Our library should accommodate at least basic listener usage. For instance, when a job execution finishes (the Kubernetes pod reports success/failure), the KubernetesScheduler can invoke JobListener.jobWasExecuted(...) with a context that includes any exception thrown. To do this, the remote execution (in the pod) should communicate back any exception or result. A simple approach is: if the job class throws an exception in execute, the JobRunner can catch it and ensure the process exits with a non-zero code. The scheduler can detect a failure via the Kubernetes Job status and then notify listeners that the job threw an exception (perhaps reconstructing an exception object if possible or at least a message). This is complex but important for full compatibility. Initially, we might log failures and not fully propagate exception objects.
Parallel Execution and Thread Pool: In Quartz, a thread pool determines how many jobs can run simultaneously. In our design, concurrency is handled by Kubernetes – each job runs in its own pod, so the limitation is the cluster capacity rather than a local thread pool. We effectively have an "infinite" (or externalized) thread pool. However, to avoid overwhelming the cluster or hitting API server QPS limits, we might impose a configurable limit on how many jobs can be dispatched at once. This could be a simple internal counter or semaphore in the scheduler. By default, it could be unbounded or a high number, but administrators can tune it (for example, "max concurrent pods = N").
Job Identity and Correlation: Each scheduled job instance (a trigger firing) should be identifiable. Quartz provides a JobKey (name/group) for JobDetail and TriggerKey for triggers. We will incorporate those. When creating a Kubernetes Job resource, we will use identifiers that correlate to these keys. For example, the Kubernetes Job name could be a combination of the job key and a firing timestamp or a unique ID. This way, if a user sees a Pod or Job in Kubernetes, they can trace it back to the Quartz job, and vice versa. Conversely, the scheduler could label Kubernetes resources with metadata (labels or annotations) indicating the Quartz job name, group, trigger, etc. This helps for debugging and also potentially for cleanup (e.g., if a job is unscheduled, any running pods for it might be terminated if desired).
Misfire and Catch-up: Quartz defines "misfire instructions" for triggers – what to do if a trigger was supposed to fire when the scheduler was shut down or busy. For instance, a CronTrigger might have instructions to fire immediately if one or more executions were missed. Our scheduler, if using a persistent store, should handle misfires on startup. If the application was down or scheduler paused, upon resume it should detect triggers that were missed and either fire them now or skip according to configuration. This logic will be built into trigger handling, similarly to Quartz's behavior.
Handling Concurrency and Non-Concurrent Jobs
Quartz allows jobs to be marked @DisallowConcurrentExecution, meaning the scheduler should not execute two instances of that job (same JobDetail) at the same time
quartz-scheduler.org
. Our implementation will honor this contract:
If a job class is annotated with @DisallowConcurrentExecution, the KubernetesScheduler will check before dispatching a new execution of the same JobDetail. If the previous execution is still running (we can track running jobs by their keys), then we have a few options:
Skip (Do Not Fire): Simply skip this occurrence. This would mimic Kubernetes CronJob's concurrencyPolicy: Forbid behavior (which skips a schedule if the previous run hasn't finished)
kubernetes.io
. The next trigger occurrence will still be scheduled normally.
Serialize (Queue): Alternatively, we could delay the new execution until the running one finishes. This is more like how Quartz actually handles non-concurrent jobs (it will wait and fire the misfired trigger as soon as possible after the running one finishes). Implementing this requires a queue or tracking for "missed" trigger occurrences.
For initial simplicity, skipping might be acceptable, but to truly mimic Quartz, we should handle it by delaying execution. The design will note this, and we might implement a basic queue for each JobKey that is non-concurrent.
If jobs are allowed to run concurrently (default), the scheduler will simply launch a new Kubernetes Job even if one is already running. Kubernetes can handle many pods in parallel, so concurrency is naturally supported. The only limit is if a job is scheduled to fire very frequently and takes a long time to run, multiple pods will accumulate. This might be desired or might overwhelm resources, so users should configure triggers and cluster resources accordingly. (We may allow a global concurrency limit as mentioned, but not a per-job unless using the above annotation.)
Overlapping triggers: If the same job has multiple triggers (Quartz allows a JobDetail to have multiple triggers), each firing could also cause overlaps. The same concurrency logic applies by JobDetail identity.
Job Data and Serialization
JobDataMap provides data to jobs (e.g., parameters for the task). We must send this data to the container so the job can use it. We will likely have to serialize the JobDataMap to a string or file, because it needs to cross the boundary from the scheduler to the pod:
A straightforward approach is to serialize the JobDataMap to JSON (limiting to primitive types and strings, which is common in Quartz usage). This JSON could be passed as an argument or environment variable to the container. For example, in the container args: --jobData {"email":"user@example.com","threshold":5}. The JobRunner inside the container will parse this JSON and populate a JobDataMap to pass to the job instance. We have to document that the data should be simple serializable types (which is generally true, as Quartz JobDataMap is often primitives or serializable objects; complex objects would not be trivially sendable unless the container has access to those classes too).
Alternatively, if the data is large or contains binary, we could mount a ConfigMap or volume. But that adds complexity. The JSON-in-arg or env is simpler. Kubernetes has a size limit on environment variable values and args (env can handle a few kilobytes, which should suffice for most cases).
We also need to consider sensitive data: If job data contains secrets, passing them as plain text in args/env might not be ideal. We could allow integration with Kubernetes Secrets (e.g., the library could detect some special handling or the user explicitly puts data in a Secret and passes a reference). However, that is probably beyond initial scope. We will note it as an extensibility/consideration.
The JobExecutionContext that the job sees in the container will be constructed by the JobRunner. It can contain the merged JobDataMap (job detail + trigger data). It might not contain everything a normal Quartz context has (like a reference to the Scheduler instance – which wouldn't be meaningful remotely, or the exact next fire time – which the job usually doesn't need to know). We will aim to make it as complete as possible for the job's needs.
Kubernetes Resource Management
One design choice is whether to use Kubernetes Job resources or directly create Pods for each execution. We will use Job resources for the following reasons:
A Kubernetes Job provides built-in retry logic and tracks completion. By creating a Job, we delegate retry and failure management to Kubernetes. For example, if a Job’s Pod fails, the Job controller will create a new Pod (up to the backoffLimit) and apply exponential backoff delays
kubernetes.io
. This saves us from reimplementing a retry loop. We can configure the backoffLimit to match what we want (perhaps expose it as a configuration per job or use the default 6).
A Job keeps a history of Pods (completed/failed) which can be useful for debugging. We can also set .spec.ttlSecondsAfterFinished to auto-clean Jobs after some time if desired (Kubernetes TTL controller feature), or use the history limits to only keep a certain number of old jobs
kubernetes.io
.
A CronJob resource (CronJob is a higher-level resource that creates Jobs on a schedule) might be tempting to use for recurring jobs. For example, if a trigger is a Cron expression, we could create a CronJob in the cluster with that schedule. However, using CronJob has trade-offs:
CronJob schedules are limited to per-minute granularity (standard cron format)
kubernetes.io
, whereas Quartz cron triggers can have per-second granularity and more complex expressions. We don’t want to lose that precision or capability.
If we delegate scheduling to Kubernetes CronJob, then the Quartz scheduler (our library) would not directly manage each firing, which could complicate integration (listeners, JobDataMap changes per execution, etc., would be harder to handle).
Also, if we create CronJobs for each scheduled job, unscheduling or updating a job means manipulating CronJob objects. This could be done (the library can create/delete CronJobs), but then we rely on Kubernetes to do the actual firing, essentially making the library somewhat passive for those jobs.
Therefore, the default design is that the library will handle all scheduling itself (using its own timing mechanism and creating one Kubernetes Job at each fire time, even for recurring jobs). This ensures it fully supports Quartz features and timing. We will mention CronJob integration as a possible future optimization or option (for example, in a mode where the library is just a translator to CronJobs for long-running schedules, to survive restarts without a DB, etc.), but not in the initial implementation.
Resource Naming and Namespace: The library should allow configuration of the Kubernetes namespace in which Jobs/Pods are created. By default, it might use a namespace provided in config or if running in cluster, perhaps the same namespace as the application. Each created Job resource will need a unique name. We could generate names using a prefix and a timestamp or a random suffix. For example: quartz-job-{jobKey}-{fireTimeMillis}. Kubernetes Job names have length limits (<= 63 chars typically), so we might truncate or hash long names. We will also use labels on the Job (e.g., jobKey=<name>, jobGroup=<group> and maybe triggerKey=<triggerId>) for identification.
Cleanup: Normally, completed Jobs (and their pods) remain in the cluster until cleaned up (unless TTL is set). Our library might provide an option to automatically delete Jobs after they finish (especially one-shot jobs). But deleting immediately might remove the ability to fetch logs or see status. A compromise is to let them live for some time or keep the last N executions. Kubernetes CronJob has .spec.successfulJobsHistoryLimit and .spec.failedJobsHistoryLimit for this purpose
kubernetes.io
. For individual Jobs we create, we could set a TTL seconds after finish. We’ll make this configurable (e.g., by default, delete a job an hour after it succeeded, or never delete if the user wants to inspect manually). This is more of an operational detail but worth noting in design.
Kubernetes Integration and Execution
Using the Kubernetes Client
The library will include a Kubernetes client to communicate with the cluster. We plan to use the Fabric8 Kubernetes Client for its ease of use and popularity in the Java community
nubenetes.com
. Fabric8 allows building and creating resources like so:
KubernetesClient client = new KubernetesClientBuilder().build();
client.batch().v1().jobs().inNamespace("jobs-namespace")
      .resource(jobObject).create();
Where jobObject is a io.fabric8.kubernetes.api.model.batch.v1.Job built via a JobBuilder. The official Kubernetes Java client could also be used (it requires writing POJOs or YAML for Job specs and is a bit more verbose). The design will encapsulate client calls behind an interface KubernetesApiService so that we could swap implementations if needed. Cluster Configuration: The client will be configured to connect to the target cluster. If the application is running inside Kubernetes, the default configuration will use the in-cluster service account. If running externally (e.g., a desktop or server outside the cluster), it will use kubeconfig or an explicit config. The library could allow specifying config via a file or context. We will rely on the client’s standard config loading (Fabric8 and official clients both can auto-configure from environment). Resource Construction: When it’s time to fire a job, the library will construct a Job object. Pseudo-code for constructing a Job using Fabric8 might look like:
Job job = new JobBuilder()
    .withNewMetadata()
        .withName(jobName)
        .withLabels(labelsMap)
    .endMetadata()
    .withNewSpec()
        .withBackoffLimit(backoffLimit)
        .withNewTemplate()
            .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                    .withName("job-runner")
                    .withImage(jobImage)
                    .withArgs(JobRunnerMainClass, "--jobClass", jobClassName, "--jobData", jobDataJson)
                .endContainer()
            .endSpec()
        .endTemplate()
    .endSpec()
    .build();
client.batch().v1().jobs().inNamespace(ns).create(job);
We ensure restartPolicy: Never (as noted, to let the Job controller handle retries via new pods rather than restarting in-place). We use the configured jobImage. The JobRunnerMainClass might be a constant defined by the library (for example, if the container’s entrypoint is not already the job runner, we might specify the main class if the image’s entrypoint is java). If the image is built such that its default entrypoint is our JobRunner, we could simply pass arguments for jobClass and data. This depends on how users build their images. Tracking Pod Status: Once the Job is created, Kubernetes will schedule a Pod. We have a few ways to track its progress:
Polling: The scheduler can periodically poll the Job status (using client API to get the Job resource and check .status.succeeded or .status.failed count). Once succeeded or failed, we consider the execution done.
Watching: Fabric8 client supports watch which can notify when a resource changes. We could set up a watch on either the specific Job or on Pods with a certain label. For simplicity, polling every few seconds might be enough. The overhead is minimal if jobs are not extremely frequent.
When the Job completes, if .status.succeeded == 1, it means the job ran successfully. If .status.failed > 0 and backoffLimit reached, it means it ultimately failed. We can then collect logs (if not already streamed) and mark the Quartz job execution as finished. Log Streaming Implementation: If log streaming is enabled, right after creating the Job, the scheduler can attempt to attach to the Pod’s logs. There is a bit of a race (the Pod might not be running yet). We can implement a small wait: watch for the Pod to be assigned/run, then start a log stream. Fabric8 allows something like:
LogWatch watch = client.pods().inNamespace(ns).withName(podName)
    .tailingLines(0)
    .watchLog(System.out);
This would pipe the pod’s log to the system output. We could also integrate it with the application logging (e.g., capture the lines and log via slf4j). If using the official client, it provides a readNamespacedPodLog call and also supports websockets for stream. We will hide these details behind a LogStreamer component. It will also handle termination: once the pod is finished (and logs stream ends), it should close resources. We need to ensure that streaming a log doesn’t block the main scheduler thread. It would be run in a separate thread or use async callback. In design, we'll dedicate either one thread per streaming (for each running job) or use a common thread pool for log streams if necessary. Since jobs can be many, maybe a thread per job is not scalable if thousands of jobs run simultaneously. An alternative is not to stream extremely long-running jobs but rather fetch logs periodically. However, for simplicity, we assume typical use where jobs are moderately sized and a streaming thread per job is acceptable. This is configurable, and users can disable streaming to avoid the overhead. Security and Permissions: If the application runs in Kubernetes, its service account must have permission to create and monitor Jobs in the target namespace. This implies some RBAC setup (allow create/get/watch on jobs/pods). This will be documented. If running externally, the kubeconfig user must have these permissions. This is not a code design issue but a deployment consideration.
Job Execution Environment (Container)
Now focusing on the Job Runner container: this is the runtime that actually executes the Job. We envision a couple of ways this could be set up:
Same Application Image Approach: The user's application itself might be packaged as a container image which includes both the scheduling library and the job classes. In such a scenario, one could use the same image to run jobs by just overriding the command. For example, the main application might normally start a Spring Boot app (including the scheduler). But for job execution, we could run the same image with a different entrypoint/command to run a specific job and exit. The benefit is that all classes (including the user’s Job implementations) are present. The drawback is the image might be large and includes unnecessary parts for a single job run. However, it ensures consistency.
Dedicated Job Runner Image: We create a separate lightweight image whose purpose is to run Quartz jobs. It might include the Quartz-Kubernetes library (or a portion of it) and perhaps requires the user’s job classes to be available. How to get the user’s classes into this image? They could be packaged as a dependency jar or the user extends a base image by adding their jar. For instance, we could provide a base image quartz-k8s-runner that contains our runner logic, and the user builds an image FROM that, adding their application jar to /app.jar. The runner would know to load /app.jar. This is more of a deployment pattern than library code, but we can document it.
Remote Class Loading: This would be an advanced approach where the job code is not pre-bundled in the image, but fetched at runtime (e.g., from a network location or ConfigMap). This is likely too complex and out-of-scope initially, but could be considered for extensibility (for example, if the library could upload the job bytecode to a ConfigMap or volume that the pod mounts). We will not pursue this in the initial design due to complexity and security concerns.
Given typical usage, the simplest assumption is that the job classes are in the image. So in code, when the container starts, it has everything needed. JobRunner Implementation: Inside the container, the entrypoint could be something like: java com.mycompany.QuartzJobRunner --jobClass com.example.jobs.MyJob --jobData '{"foo":"bar"}'. The QuartzJobRunner class (provided by our library or the base image) does roughly the following:
Parses arguments (gets the job class name and the data JSON).
Initializes a minimal Quartz Scheduler context (or at least constructs a JobExecutionContext object). It likely doesn’t have a real scheduler, but it can create a dummy Scheduler reference or a simple implementation if needed by the job (e.g., if the job calls context.getScheduler() for something, maybe we can provide limited functionality or stub).
Instantiates the job class using reflection (Class.forName(jobClassName).newInstance() or using an appropriate class loader).
Populates a JobDataMap from the JSON for the context.
Calls jobInstance.execute(jobExecutionContext). Surround this call with try-catch to capture any exception.
If the job throws an exception, the runner can log it and set an exit code (non-zero). The Kubernetes Job will then mark the Pod as failed. (We might also output the stack trace to stdout so it appears in logs).
If the job completes successfully, exit with code 0. (We might still output a completion message).
We could also support PersistJobDataAfterExecution and JobDataMap result passing: Quartz has an annotation @PersistJobDataAfterExecution which indicates that changes to the JobDataMap by the job should be persisted for next time. Since our executions are stateless (each in a new process), we’d need to send updated data back. This is complex – one approach is to output a special JSON to stdout that the scheduler can parse. That is probably overkill; instead, we might not fully support automatically capturing changed data in this version. We can note this limitation.
Container Resource Requirements: The design should consider that each job pod might need specific resources (CPU, memory). The library can allow configuration of default resources for job pods (e.g., via a Kubernetes ResourceRequirements setting). Perhaps users can specify in config: default CPU request/limit, memory request/limit for job containers. Or if different jobs have different needs, they might choose to specify an alternate image or have the job itself request certain resources. Since Quartz’s API doesn’t have a notion of resource requirements, this could be handled via job-specific data or configuration mapping (for example, in a properties file mapping job names to resource settings). For now, we will mention that the library could provide a config hook for a Pod template or resource limits that apply to all jobs, and possibly extended to per-job settings via job metadata.
Failure Handling and Retries
Using Kubernetes Job’s retry mechanism was already described (backoffLimit, etc.). To integrate this with Quartz semantics:
If a job fails and Kubernetes retries it successfully on second attempt, from the perspective of the Quartz scheduler, should this be reported as a failure or success? In Quartz, if a job throws an exception and is not caught, Quartz will consider that execution failed and will invoke listeners’ jobWasExecuted with an exception, and possibly refire immediately if it was a misfire or wait for next trigger. There is no built-in retry-on-failure in Quartz except via trigger misfire instructions or scheduling another job.
In our case, Kubernetes is handling the retry transparently. We might decide that a job execution is only considered failed if all retries fail (i.e., the Kubernetes Job as a whole has failed). This makes sense because the user likely wants the job to eventually succeed if possible. So we will treat the Kubernetes Job’s final result as the outcome of that trigger firing. If it succeeded on a retry, we will treat it as success (and maybe log that it succeeded after N retries). If it exhausts retries and fails, we treat it as a job execution failure.
We can expose configuration for backoffLimit. A global default (6 as per Kubernetes default
kubernetes.io
) could be used, but the user might want to override per job. Perhaps they can specify in the JobDataMap something like "k8sBackoffLimit": 0 for no retries, or in a global config map listing job names and their retry counts. Initially, a simpler approach: have a global default and allow override via a property in JobDetail or Trigger (for example, an extended interface KubernetesTrigger with a method getBackoffLimit() or just use JobDataMap).
What about backoff timing? Kubernetes handles exponential backoff delays (doubling up to 6 minutes max)
kubernetes.io
. We probably don't need to modify that, although Kubernetes 1.21+ allows setting a podFailurePolicy to fine-tune which failures count, but that's advanced usage.
Failure reporting: If a job ultimately fails, the library should mark that trigger execution as failed. In Quartz, if a job throws an exception, by default it just logs it and continues (unless the exception is a special type that can refire immediately). We can simulate this by logging the failure in the application logs. If the user has a JobListener, call jobWasExecuted with the exception in the JobExecutionContext. The actual exception object may not be easily transferred (different process), but we could wrap the exception message from the container logs into a new Exception type (e.g., JobExecutionException with message "Job failed with exit code X. See logs for details.").
For one-shot jobs, if failed, there's no next trigger unless user scheduled them to repeat or handles it manually. For recurring jobs, the failure of one occurrence does not necessarily unschedule future occurrences (Quartz would still schedule the next occurrence normally, unless the job is non-recoverable and the scheduler was shutdown mid-run, etc.). We will follow that: even if one run fails, the Cron trigger will still schedule the next run as long as the schedule is still active.
Configuration Examples
Below are examples demonstrating how a developer might configure and use the Quartz-Kubernetes Scheduler library in code and configuration files:
Library Initialization
Suppose we provide a KubernetesSchedulerFactory similar to Quartz’s StdSchedulerFactory. Usage might look like:
import com.mycompany.quartzk8s.KubernetesSchedulerFactory;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

public class SchedulerApp {
    public static void main(String[] args) throws Exception {
        // Create scheduler factory and configure
        KubernetesSchedulerFactory factory = new KubernetesSchedulerFactory();
        factory.setKubernetesNamespace("jobs");       // Namespace to run jobs in
        factory.setJobContainerImage("mycompany/app-jobs:latest"); // Image that has job classes
        factory.setMaxConcurrentJobs(10);             // (Optional) throttle concurrency
        factory.enableLogStreaming(true);             // Stream logs from pods to console

        // Optionally configure persistence
        factory.useJdbcStore("jdbc:postgresql://db:5432/quartz", "user", "pass");
        // Or: factory.useKubernetesCRDStore();

        // Get Scheduler instance
        Scheduler scheduler = factory.getScheduler();
        scheduler.start();

        // Define a job and trigger (same as Quartz usage)
        JobDetail job = newJob(MyJob.class)
            .withIdentity("myJob", "group1")
            .usingJobData("someKey", "someValue")  // JobDataMap entry
            .build();

        Trigger trigger = newTrigger()
            .withIdentity("myTrigger", "group1")
            .startNow()
            .withSchedule(simpleSchedule()
                .withIntervalInSeconds(30)
                .repeatForever())
            .build();

        // Schedule the job
        scheduler.scheduleJob(job, trigger);
    }
}
In a Spring Boot application, one could define a bean for Scheduler via a factory as well, pointing to this custom implementation, with similar properties in application.yaml (if we integrate configuration properties).
Kubernetes Cluster Preparation
The cluster needs to know about the image mycompany/app-jobs:latest. This image should include MyJob.class and any other job classes used. For example, the Dockerfile might look like:
FROM eclipse-temurin:17-jre-alpine
COPY app.jar /app.jar
# Entry point can be a shell that waits for args to run jobs
ENTRYPOINT ["java", "-cp", "/app.jar", "com.mycompany.quartzk8s.JobRunner"]
In this case, our library’s JobRunner class is the entrypoint. It expects environment variables or command-line parameters for the job to run. The scheduler, when creating a Job, will set those parameters (--jobClass, etc.) as shown earlier. Alternatively, if the entrypoint itself is flexible, we could override the entrypoint in the Kubernetes Job spec to run the JobRunner class with arguments. The user must also ensure RBAC is set so that the scheduler can create Jobs in the "jobs" namespace. For example, create a Role/ClusterRole and bind it to the service account of the app.
Running and Logs
When the above code schedules MyJob with a trigger, the library will output something like (log messages for illustration):
INFO  - Scheduled job 'group1.myJob' with trigger 'group1.myTrigger' to run at 2025-06-05T13:00:00Z
INFO  - Creating Kubernetes Job: quartz-job-myJob-1622907600000 in namespace 'jobs'
INFO  - Job Pod quartz-job-myJob-...-abcd starting... (Image: mycompany/app-jobs:latest)
If log streaming is enabled, as soon as the Pod starts, you might see the output from inside the job:
[Job: group1.myJob] Starting execution in Kubernetes Pod (Node aks-node-1)...
[Job: group1.myJob] Doing work... 
[Job: group1.myJob] Completed successfully.
(These messages would be coming from within the container’s stdout, prefix added by the JobRunner for clarity). After completion:
INFO - Job 'group1.myJob' [trigger 'group1.myTrigger'] completed at 13:00:05 (duration 5s)
If something went wrong, e.g., an exception, the log might show:
[Job: group1.myJob] ERROR: Exception in job execution: java.lang.RuntimeException: ...
... (stack trace) ...
ERROR - Job 'group1.myJob' failed after 3 retries, marked as FAILED.
And the scheduler could throw a JobExecutionException or at least log that the job execution failed.
Persistent Store Config
If using the JDBC store for persistence, the library might reuse Quartz’s table schema (so an existing Quartz database could be used). In the above snippet, factory.useJdbcStore(jdbcUrl, user, pass) would configure the scheduler to store job details and triggers in that DB. This means if the application restarts, it can retrieve the scheduled jobs from the DB and resume (possibly firing misfired triggers). It also means multiple instances could be running and coordinate via DB locks (just like Quartz clustering
quartz-scheduler.org
). In clustered mode, we'd ensure only one instance launches a given job at a time. Achieving that likely means leveraging the same locking queries Quartz uses, or implementing a simpler "leadership" approach. If using a Kubernetes CRD store, we might create a CRD like:
apiVersion: mycompany.com/v1
kind: ScheduledJob
metadata:
  name: group1.myJob
spec:
  schedule: "*/30 * * * * *"    # cron expression (Quartz style with seconds)
  image: "mycompany/app-jobs:latest"
  jobClass: "com.myapp.MyJob"
  data: {"someKey": "someValue"}
  suspend: false
status:
  lastScheduleTime: "2025-06-05T13:00:00Z"
  lastStatus: "Succeeded"
The library would create such CRDs for each scheduled job. However, unless there’s a controller in the cluster to act on them, they serve mainly as a persistent record. Our scheduler can on startup list these CRDs and reconstruct the schedule. (Future extension: a Kubernetes Operator could watch ScheduledJob CRDs and create Job objects on schedule, offloading the scheduling from the app entirely. But that would require writing a controller. For now, the Java app itself is the “controller”.)
Extensibility and Future Considerations
The design above covers the core functionality, but we have kept in mind several extension points and future improvements:
Pluggable Job Store: As noted, we plan to allow different persistence strategies. The library could define a JobStore interface (similar to Quartz’s JobStore SPI). Initially an in-memory store is default. A JDBC implementation can be added (possibly reusing Quartz’s table schema for compatibility, or a custom simplified schema). The CRD-based store is another option. The library should make it easy to plug in a new store without changing the core scheduling logic. This is useful if, for example, someone wants to use a distributed cache or another system to store schedules.
High Availability & Clustering: In critical systems, you might run multiple instances of the scheduler for failover. Quartz DB clustering would work if we implement a JDBC store with locks
quartz-scheduler.org
. Another approach native to Kubernetes is leader election. We could implement a leader election (e.g., using Kubernetes Lock/Lease object or a library) so that out of N instances, one is the active scheduler and others are standby. If the leader dies, a standby takes over scheduling. During failover, some triggers might be missed, so combining this with persistent store and misfire handling is important. This is a complex area but the design is mindful of it. Initially, we might document that only one scheduler instance should be active, but provide guidelines for HA via external tools or DB clustering. Eventually, adding built-in leader election (especially if using the Kubernetes API anyway) is a nice addition.
Kubernetes CronJob Integration: As discussed, an optional optimization could be to use CronJob for long-running recurring schedules. For example, if a job is scheduled to run every day at 6am via a Cron trigger, the library could (if configured to do so) create a CronJob resource in Kubernetes instead of managing it in-process. This would mean even if the app goes down, Kubernetes would still launch the job at 6am. The downside is the loss of second-level precision and Quartz-specific behavior. We could make this an opt-in feature per trigger (e.g., if trigger is a daily/ hourly cron, allow offload). The design easily allows adding this: the scheduleJob method can detect if a trigger is Cron and an option is enabled, then create a CronJob resource. We’d then not schedule it internally at all. We must then also watch that CronJob for events if we want to propagate job execution events back (or perhaps treat it as fire-and-forget, simply logging that the job is now under Kubernetes control). This is left for future work due to the complexity of synchronizing state.
Enhanced Logging and Monitoring: We focused on streaming logs to the console, but extensibility can include integrating with logging frameworks. For instance, the library could allow plugging a custom log handler to receive pod log lines and log them with the application’s logger (with MDC data like job name). We can also integrate metrics: track how many jobs succeeded/failed, execution durations, etc. These could be exposed via JMX or published to a monitoring system. This would help in large deployments to observe scheduler behavior.
Supporting Complex Pod Templates: Some jobs might require more than just a single container with the job code. Perhaps they need sidecar containers (for example, a job that needs a local PostgreSQL to run tests, etc.), or volumes mounted (to access a dataset), or custom environment (config maps, secrets). Our current design uses a simple generated pod spec. We could allow a pod template to be specified for jobs. For example, a YAML snippet or a reference to a Kubernetes PodTemplate resource that the library can use as a base. The library would then inject the necessary job-specific overrides (like container image tag or job class args) into that template. This gives advanced users full control over the pod environment. This is an advanced feature; initially, we might just support basic customization (image, resources) via config properties. But the architecture can be extended to accept a PodSpec object or template per job type.
Security and Isolation: Running code in containers adds security concerns (which are also benefits if done right). We might allow configuring security context for pods (run as a certain user, etc.), or use PodSecurityPolicies (or Pod Security Standards). Also, if multiple jobs run in parallel, they are isolated by containers, which is good. But note that if they all pull from the same image, the image must have all needed code. If a user wanted to run two very different types of tasks on the same scheduler, they might even use different images. This could be achieved by adding an attribute in JobDataMap like "k8sImage": "custom/image:tag" which the scheduler will use for that job execution instead of the default. We plan to support such per-job overrides for image (and potentially other settings like resources). This provides flexibility to use the right container for the right job.
Result Handling: Sometimes jobs produce a result that the application needs. Quartz usually handles this by the job itself storing results somewhere (since execute() has no return and one cannot directly get a result except via job data or listeners). In our scenario, the job could, for example, save results to a database or send a message. But if needed, we could implement a way for the JobRunner to capture a result value and communicate it back. For instance, if we standardize that the job can put a result in the JobDataMap under a known key by end of execution, the JobRunner could serialize that and perhaps annotate the Kubernetes Job or write to a temporary file that the scheduler reads. This is complicated and would introduce tight coupling, so it's not a priority for now. We assume jobs handle their outputs externally as needed. Our focus is on scheduling and execution.
Testing and Local Execution Mode: It might be useful to have a mode where the scheduler can execute jobs locally (for unit testing or development without a cluster). We could allow the Kubernetes dispatcher to have a "local" implementation that simply invokes the job in-process. This could be triggered by configuration (e.g., if kubernetes.enabled=false, then it falls back to a local mode). This would let developers run jobs without needing a cluster, albeit without isolation. Since our library is meant to imitate Quartz, one could also just use Quartz locally, but having a single code path is convenient. This is a nice-to-have for developer experience.
Documentation and Examples: As part of extensibility, ensuring we have clear guides for how to build the container image, how to configure RBAC, how to set up persistence, etc., is crucial. This isn't a code feature, but the design should include delivering example configs (Kubernetes YAMLs, Dockerfiles, etc.) in the repository so users can get started easily.
Kubernetes API Version Upgrades: The library should be maintained to support newer Kubernetes versions. For instance, in newer versions, CronJob moved to batch/v1 (which we target), new fields like timeZone were introduced for CronJobs
kubernetes.io
, etc. We must keep the client library up-to-date and consider version compatibility. Using fabric8, which often supports multiple k8s versions, will help. Alternatively, generating our own client from the OpenAPI could tie to a specific version. We might choose to support Kubernetes 1.20+ at minimum, given CronJob and Job features are quite stable by then.
In summary, the design provides a robust starting point for a Quartz-compatible scheduler that delegates execution to Kubernetes. By keeping the same API, we make it easy for existing projects to adopt cloud-native job execution without rewriting their scheduling logic. At the same time, we leverage Kubernetes features (retries, cron, scaling, isolation) to run jobs more reliably and flexibly. The architecture is modular, allowing for future enhancements like different persistence layers, deeper Kubernetes integration (operators), and custom execution environments. With this design document as a foundation, the next step would be to implement the components, starting with the core scheduling logic, the Kubernetes dispatcher, and a simple job runner, then iteratively adding features like persistence and log streaming. The document is detailed enough to guide implementation and can be included in the repository to help developers (or AI code generators) understand the intended structure and behavior of the system.

Favicon
Favicon
Favicon
Sources



No file chosenNo file chosen
ChatGPT can make mistakes. Check important info.

Preview


Download PDF

Share Link
Quartz-Kubernetes Scheduler Design Document
Introduction
Quartz is a popular open-source job scheduling library for Java that allows applications to schedule and run jobs (tasks) at specified times or intervals. The goal of this project is to create a Java library that is API-compatible with Quartz (implementing the same key interfaces such as Scheduler, Job, JobDetail, Trigger, etc.
quartz-scheduler.org
) while offloading the execution of jobs to a Kubernetes cluster. Instead of running scheduled jobs on the local JVM (threads), each job execution will be dispatched to run in a Kubernetes Pod (or a Kubernetes Job which manages Pods). This allows leveraging Kubernetes for resource management, isolation, scaling, and reliability of job executions. By preserving the Quartz API, Java developers can use this library as a drop-in replacement for Quartz with minimal changes to their code. They can define jobs and triggers in the same way, but the actual work will run in containers on Kubernetes. This document outlines the design of the library, including its architecture, how it interfaces with Kubernetes, how it remains compatible with Quartz, and configuration and extensibility considerations.
Goals and Requirements
Quartz API Compatibility: The library will implement the core Quartz Scheduler interfaces (Scheduler, Job, JobDetail, Trigger, etc.) so that existing code using Quartz can switch to this implementation without significant changes
quartz-scheduler.org
. The scheduling DSL (e.g. using JobBuilder, TriggerBuilder) and listener mechanisms should behave similarly to Quartz where possible. The Scheduler provided by this library should accept jobs and triggers and manage their lifecycle analogous to a standard Quartz scheduler.
Kubernetes Pod Execution: Instead of executing jobs on local threads, each job firing is executed in a Kubernetes cluster as a separate Pod (encapsulated in a Kubernetes Job resource for reliability). The library will create a Kubernetes Job/Pod for each execution of a JobDetail when a trigger fires. This means the actual execute() method of the Quartz Job runs inside a container on the cluster, not in the original JVM process.
Support One-shot and Recurring Jobs: Both one-time jobs (with a specific scheduled time or run immediately) and recurring jobs (with simple intervals or CRON expressions) must be supported, similar to Quartz’s support for one-shot triggers and cron triggers. Cron-based scheduling should support Quartz cron expressions (which include seconds) for fine-grained scheduling, as well as simple repeated intervals.
Configurable Job Container Image: The Docker image used for running the job code in Kubernetes should be configurable. Developers might provide a default image that contains their job classes and all necessary dependencies. The library should allow specifying this image via configuration (and possibly override per job if needed). The container image must have access to the job’s compiled classes (see "Job Execution Environment" below).
Logging to Stdout/Stderr with Streaming Option: Jobs running in Kubernetes will output logs to the container’s stdout/stderr. This output should be accessible to developers. The design will ensure logs from the job’s container are available, ideally in a console-friendly format. An optional feature will be to stream these logs back to the application that scheduled the job (in real-time or near-real-time) so that from a developer’s perspective, it feels similar to seeing job logs in the local console. This might be achieved by attaching to Pod logs via the Kubernetes API. If not streaming, the logs can at least be fetched on completion or viewed via standard kubectl logs mechanisms.
Job Class Accessibility in Container: The implementation must address how the job’s Java class (which implements org.quartz.Job) is made available inside the container that runs the job. A common pattern is assumed: for example, the container image might include the application’s JAR or classes, so that the job class can be loaded and executed. We will design a Job Runner mechanism that the container uses to locate and run the specified Job class. This could involve passing the job’s class name and data to the container as environment variables or command-line arguments.
Use of Kubernetes Java Client: Interactions with the Kubernetes API should use a popular Java Kubernetes client library. We may use the official Kubernetes Java client or the Fabric8 Kubernetes Client. Fabric8 has been a widely-used Kubernetes client for Java (one of the most popular since 2015)
nubenetes.com
, offering a fluent DSL and resource watchers, whereas the official client is maintained by Kubernetes contributors and aligns closely with k8s API conventions. The design will favor using one of these (with a lean toward Fabric8 for its simplicity) and abstract the client usage so it could be swapped or upgraded in the future.
Retry, Backoff, and Failure Handling: The system should leverage Kubernetes’ native features to handle job retries and failures. When creating Kubernetes Job resources for each execution, we can set parameters like .spec.backoffLimit (the number of retries before marking the job as failed, default 6 in Kubernetes
kubernetes.io
) and let Kubernetes perform exponential back-off between retries
kubernetes.io
. The library should surface failure information (e.g., if a job fails after all retries) back to the developer through the Quartz API where possible (perhaps via job listener callbacks or error logs). It should also consider using Kubernetes Job status to determine success/failure.
Persistent Job Store (Optional): In Quartz, a persistent job store (e.g. JDBC database) is used for storing job schedules and state, enabling recovery after restarts and clustering. This library design should allow optional persistence of job and trigger definitions, either by integrating with a database (similarly to Quartz’s JDBCJobStore) or by using Kubernetes custom resources (CRDs) to store the schedule. Using a database would allow multiple scheduler instances to coordinate (for HA/clustering) via locking, as Quartz does
quartz-scheduler.org
. Using Kubernetes CRDs would make the schedule declarative in the cluster (and could enable a future Kubernetes-native operator to handle scheduling). The design will include an interface for a Job Store and provide at least an in-memory implementation by default, with extension points for a JDBC store or CRD-based store.
Minimal Application Changes: The library should require minimal changes to existing Quartz-using applications. For example, a user might only need to initialize the scheduler via a different factory class (e.g., KubernetesSchedulerFactory instead of StdSchedulerFactory) or change some configuration, and then use the Scheduler as usual. All job definitions, trigger configurations, and listener registrations would remain the same.
Architecture Overview
The Quartz-Kubernetes Scheduler library consists of several components working together to achieve the above goals. At a high level, the architecture is split between the scheduling side (Java application) and the execution side (Kubernetes cluster):
 Java Application (Scheduler)                       Kubernetes Cluster (Execution)
 --------------------------------                   --------------------------------
 | Quartz-compatible API (Scheduler) |             | Kubernetes API Server         |
 |  - Stores JobDetail & Triggers    |             |  - Accepts Job/Pod specs      |
 |  - Determines next execution time |             |  - Schedules Pods on Nodes    |
 |  - Dispatches jobs to K8s        |             |                              |
 |                                  |             |  Pod (Job Execution)          |
 | Kubernetes Client (Fabric8/Official)|----->    |  - Runs JobRunner container    |
 |  - Connects to cluster API       |    API      |  - Executes Job class          |
 |  - Creates Job/Pod resources     |   calls     |  - Outputs logs to stdout      |
 |  - Monitors job status           |             |  - Reports status on finish    |
 |                                  |             |                              |
 | Optional Persistent Job Store    |             | Kubernetes Controllers         |
 |  (DB or CRD) for HA/Recovery     |             |  - Job Controller (handles retry/backoff) |
 --------------------------------                   --------------------------------
Figure: Architecture Overview – The scheduler library runs within the Java application and interacts with the Kubernetes cluster via the Kubernetes API. Each scheduled job execution results in a Kubernetes Job/Pod being created. Kubernetes runs the pod (in a suitable node), and the job’s Java code executes inside that pod. The library monitors the Kubernetes job status and updates its internal state accordingly. Key architectural components and their roles:
Quartz Compatibility Layer: This is the set of classes that implement Quartz interfaces (Scheduler, JobDetail, Trigger, etc.) and provide the same methods and behavior (as much as possible). For example, KubernetesScheduler might implement Quartz’s Scheduler interface. It will manage job scheduling, trigger registration, listeners, etc., but delegate execution to Kubernetes. It uses an internal scheduler loop or timer to track trigger firing times (similar to Quartz's scheduling threads).
Job Store: An interface/abstract component that manages storage of job and trigger metadata. The default implementation might be in-memory (non-persistent), suitable for simple deployments or development. For persistence, a JDBC-based store can be provided (storing jobs/triggers in a relational database similar to Quartz’s tables) so that jobs survive application restarts and multiple instances can coordinate. Another implementation could use Kubernetes CRDs (Custom Resource Definitions) to persist job schedules in the cluster itself. Custom resources are extensions of the Kubernetes API used to store domain-specific objects
kubernetes.io
, which in our case could represent scheduled jobs. For example, a ScheduledJob CRD might contain the cron expression, job class, and other details. This data would live in etcd (Kubernetes backing store) and be accessible even if the scheduler app restarts.
Kubernetes Dispatcher: This component encapsulates the Kubernetes client interactions. It is responsible for translating a Quartz job execution request into a Kubernetes Job (or Pod) object and using the Kubernetes API to create it in the cluster. It also watches or polls for the status of the job. The dispatcher uses the Java Kubernetes client (Fabric8 or official). Fabric8’s fluent API can simplify creating resource definitions in code. For example, creating a Job with Fabric8 might involve constructing a JobBuilder with metadata, specifying backoffLimit, etc., instead of writing raw YAML. The dispatcher abstracts these details away from the core scheduler logic.
Job Runner Container: This refers to the runtime environment inside the Kubernetes Pod that actually runs the Job.execute(...) method. The container image for the job runner is configurable. Typically, it will be an image that includes the application’s code or at least the classes necessary to run the job. One design is to include a JobRunner Java program in the image – this program’s responsibility is to accept parameters (like which job class to run, and job data) and then use reflection or a plugin system to instantiate the job class and call its execute() method. The job runner might be invoked by specifying the main class and arguments in the Pod spec. For example, the Kubernetes dispatcher could create a Job with a Pod template like:
apiVersion: batch/v1
kind: Job
metadata:
  name: quartz-job-myJob123
spec:
  backoffLimit: 6                # Retries before failure (default 6):contentReference[oaicite:7]{index=7}
  template:
    spec:
      restartPolicy: Never       # Don't restart within the same Pod; use new Pod for retries:contentReference[oaicite:8]{index=8}
      containers:
      - name: job-runner
        image: mycompany/jobs:1.0   # Configured job image containing the code
        args: ["com.myapp.JobRunner", "--jobClass", "com.myapp.MyJob", "--jobData", "{\"key\":\"value\"}"]
        env:
          - name: JOB_NAME
            value: "myJobInstanceId"
In the above, JobRunner is a Java class (part of our library or provided by the user) that knows how to read the --jobClass and --jobData arguments or environment variables, load that class (which is on the classpath in the container), and execute it. The restartPolicy: Never is used so that if the job fails, Kubernetes will treat the Pod as failed and, via the Job controller, create a new Pod if retries remain
kubernetes.io
. The backoffLimit: 6 means Kubernetes will try up to 6 retries by default
kubernetes.io
, doubling the wait time after each failure (10s, 20s, 40s... capped at 6 minutes between tries) for exponential backoff.
Logging and Monitoring: The library will also include components for log streaming and monitoring execution. When a Kubernetes Job/Pod is launched, the scheduler can attach to it to stream logs. This might be implemented via a separate thread using the Kubernetes client’s log API (for example, Fabric8 client can fetch logs or follow logs of a Pod). If enabled, this will forward the container’s stdout/stderr to the application’s console or logging framework. This makes the remote execution more transparent. Alternatively, if streaming is disabled, the user can rely on kubectl logs or cluster logging tools to view output. The library will also monitor the status of each job execution (perhaps by watching the Job resource or polling for its completion) so it knows when the execution is finished, and whether it succeeded or failed.
Below is a sequence diagram illustrating the interactions when scheduling and executing a job:
sequenceDiagram
    participant UserApp as User Application (Using Scheduler)
    participant Scheduler as K8sQuartzScheduler (Library)
    participant API as Kubernetes API Server
    participant Pod as Job Pod (Container)
    UserApp->>Scheduler: scheduleJob(JobDetail, Trigger)
    Scheduler->>Scheduler: Store job & trigger (JobStore)
    Scheduler->>Scheduler: Calculate next fire time for trigger
    UserApp-->Scheduler: (Scheduler runs in background after start())
    Note over Scheduler: Scheduler thread waits until trigger's fire time
    Scheduler->>API: Create Kubernetes Job (with Pod template for the job)
    API-->>Scheduler: Job accepted (API response)
    API-->>Pod: Launch Pod (pull image, start container)
    Pod->>Pod: Initialize JobRunner with job class & data
    Pod->>Pod: Execute job logic (Job.execute())
    Pod-->>API: Report completion (success or fail status)
    Scheduler->>API: (Optionally stream logs during execution)
    Scheduler->>API: Monitor Job status (poll or watch)
    API-->>Scheduler: Job status = Succeeded/Failed
    Scheduler->>Scheduler: Mark execution done; update internal state
    Scheduler->>Scheduler: For recurring trigger, compute next fire time
    Scheduler->>UserApp: (Optional) Notify listeners or handle result
Figure: Job Scheduling and Execution Flow – The application schedules a job via the Quartz-like API. The library stores the job and waits for the trigger to fire. When it's time, the library uses the Kubernetes API to create a Job/Pod to execute the job. The Pod runs the specified job class inside a container. The library optionally streams logs and monitors the job’s completion. Finally, it updates the schedule for the next run if the trigger is recurring.
Quartz Compatibility Layer Design
Scheduler Interface Implementation
At the heart of the library is the implementation of the org.quartz.Scheduler interface (or an equivalent interface if we avoid depending on Quartz artifacts directly). The Scheduler implementation (let's call it KubernetesScheduler) is responsible for managing job scheduling just like the Quartz StdScheduler. Key points in the design:
The KubernetesScheduler will have methods like scheduleJob(JobDetail job, Trigger trigger), pauseTrigger(TriggerKey), deleteJob(JobKey), etc., matching Quartz API. Internally, when a job is scheduled, the scheduler will store the job metadata (perhaps in an in-memory map or via the Job Store) and compute the next fire time based on the Trigger. It does not execute the job immediately in the local JVM. Instead, it will rely on a timing mechanism to know when the trigger's fire time is reached.
A scheduling loop or thread pool will be used to wait for triggers to fire. This could be implemented with a dedicated scheduler thread that sleeps until the next trigger time, or using a delay queue of triggers. (We could potentially reuse some Quartz code for trigger calendars and misfire handling if license allows, or implement our own simplified logic.)
Trigger types: We must support Simple triggers (one-shot or fixed interval repeats) and Cron triggers (based on cron expressions). The library can provide implementations of SimpleTrigger and CronTrigger that mirror Quartz’s. For example, a CronTrigger might hold a cron expression and use Quartz’s CronExpression parser or a similar utility to compute next fire times. Quartz cron expressions include seconds and other advanced features, which we will support. If using Quartz’s own classes (if the Quartz library is on the classpath), we could accept a org.quartz.CronTrigger and read its properties. If not, we can implement a parser.
JobDetail and Job: The JobDetail holds the job’s definition (job class, identity, durable or not, etc.) and a JobDataMap of data. We will likely use the same JobDetail and JobDataMap classes from Quartz or provide clones. The Job interface (with the execute(JobExecutionContext) method) will remain the same. Developers will implement this interface for their jobs. However, unlike standard Quartz, calling execute will happen in a remote container. The JobExecutionContext provided to the job might be a limited version since not all aspects make sense remotely (for example, a reference to the Scheduler or Trigger might not be fully available unless we serialize the necessary info).
Listeners and Hooks: Quartz allows job listeners, trigger listeners, and scheduler listeners to hook into events (job to be executed, job execution complete, trigger misfired, etc.). Our library should accommodate at least basic listener usage. For instance, when a job execution finishes (the Kubernetes pod reports success/failure), the KubernetesScheduler can invoke JobListener.jobWasExecuted(...) with a context that includes any exception thrown. To do this, the remote execution (in the pod) should communicate back any exception or result. A simple approach is: if the job class throws an exception in execute, the JobRunner can catch it and ensure the process exits with a non-zero code. The scheduler can detect a failure via the Kubernetes Job status and then notify listeners that the job threw an exception (perhaps reconstructing an exception object if possible or at least a message). This is complex but important for full compatibility. Initially, we might log failures and not fully propagate exception objects.
Parallel Execution and Thread Pool: In Quartz, a thread pool determines how many jobs can run simultaneously. In our design, concurrency is handled by Kubernetes – each job runs in its own pod, so the limitation is the cluster capacity rather than a local thread pool. We effectively have an "infinite" (or externalized) thread pool. However, to avoid overwhelming the cluster or hitting API server QPS limits, we might impose a configurable limit on how many jobs can be dispatched at once. This could be a simple internal counter or semaphore in the scheduler. By default, it could be unbounded or a high number, but administrators can tune it (for example, "max concurrent pods = N").
Job Identity and Correlation: Each scheduled job instance (a trigger firing) should be identifiable. Quartz provides a JobKey (name/group) for JobDetail and TriggerKey for triggers. We will incorporate those. When creating a Kubernetes Job resource, we will use identifiers that correlate to these keys. For example, the Kubernetes Job name could be a combination of the job key and a firing timestamp or a unique ID. This way, if a user sees a Pod or Job in Kubernetes, they can trace it back to the Quartz job, and vice versa. Conversely, the scheduler could label Kubernetes resources with metadata (labels or annotations) indicating the Quartz job name, group, trigger, etc. This helps for debugging and also potentially for cleanup (e.g., if a job is unscheduled, any running pods for it might be terminated if desired).
Misfire and Catch-up: Quartz defines "misfire instructions" for triggers – what to do if a trigger was supposed to fire when the scheduler was shut down or busy. For instance, a CronTrigger might have instructions to fire immediately if one or more executions were missed. Our scheduler, if using a persistent store, should handle misfires on startup. If the application was down or scheduler paused, upon resume it should detect triggers that were missed and either fire them now or skip according to configuration. This logic will be built into trigger handling, similarly to Quartz's behavior.
Handling Concurrency and Non-Concurrent Jobs
Quartz allows jobs to be marked @DisallowConcurrentExecution, meaning the scheduler should not execute two instances of that job (same JobDetail) at the same time
quartz-scheduler.org
. Our implementation will honor this contract:
If a job class is annotated with @DisallowConcurrentExecution, the KubernetesScheduler will check before dispatching a new execution of the same JobDetail. If the previous execution is still running (we can track running jobs by their keys), then we have a few options:
Skip (Do Not Fire): Simply skip this occurrence. This would mimic Kubernetes CronJob's concurrencyPolicy: Forbid behavior (which skips a schedule if the previous run hasn't finished)
kubernetes.io
. The next trigger occurrence will still be scheduled normally.
Serialize (Queue): Alternatively, we could delay the new execution until the running one finishes. This is more like how Quartz actually handles non-concurrent jobs (it will wait and fire the misfired trigger as soon as possible after the running one finishes). Implementing this requires a queue or tracking for "missed" trigger occurrences.
For initial simplicity, skipping might be acceptable, but to truly mimic Quartz, we should handle it by delaying execution. The design will note this, and we might implement a basic queue for each JobKey that is non-concurrent.
If jobs are allowed to run concurrently (default), the scheduler will simply launch a new Kubernetes Job even if one is already running. Kubernetes can handle many pods in parallel, so concurrency is naturally supported. The only limit is if a job is scheduled to fire very frequently and takes a long time to run, multiple pods will accumulate. This might be desired or might overwhelm resources, so users should configure triggers and cluster resources accordingly. (We may allow a global concurrency limit as mentioned, but not a per-job unless using the above annotation.)
Overlapping triggers: If the same job has multiple triggers (Quartz allows a JobDetail to have multiple triggers), each firing could also cause overlaps. The same concurrency logic applies by JobDetail identity.
Job Data and Serialization
JobDataMap provides data to jobs (e.g., parameters for the task). We must send this data to the container so the job can use it. We will likely have to serialize the JobDataMap to a string or file, because it needs to cross the boundary from the scheduler to the pod:
A straightforward approach is to serialize the JobDataMap to JSON (limiting to primitive types and strings, which is common in Quartz usage). This JSON could be passed as an argument or environment variable to the container. For example, in the container args: --jobData {"email":"user@example.com","threshold":5}. The JobRunner inside the container will parse this JSON and populate a JobDataMap to pass to the job instance. We have to document that the data should be simple serializable types (which is generally true, as Quartz JobDataMap is often primitives or serializable objects; complex objects would not be trivially sendable unless the container has access to those classes too).
Alternatively, if the data is large or contains binary, we could mount a ConfigMap or volume. But that adds complexity. The JSON-in-arg or env is simpler. Kubernetes has a size limit on environment variable values and args (env can handle a few kilobytes, which should suffice for most cases).
We also need to consider sensitive data: If job data contains secrets, passing them as plain text in args/env might not be ideal. We could allow integration with Kubernetes Secrets (e.g., the library could detect some special handling or the user explicitly puts data in a Secret and passes a reference). However, that is probably beyond initial scope. We will note it as an extensibility/consideration.
The JobExecutionContext that the job sees in the container will be constructed by the JobRunner. It can contain the merged JobDataMap (job detail + trigger data). It might not contain everything a normal Quartz context has (like a reference to the Scheduler instance – which wouldn't be meaningful remotely, or the exact next fire time – which the job usually doesn't need to know). We will aim to make it as complete as possible for the job's needs.
Kubernetes Resource Management
One design choice is whether to use Kubernetes Job resources or directly create Pods for each execution. We will use Job resources for the following reasons:
A Kubernetes Job provides built-in retry logic and tracks completion. By creating a Job, we delegate retry and failure management to Kubernetes. For example, if a Job’s Pod fails, the Job controller will create a new Pod (up to the backoffLimit) and apply exponential backoff delays
kubernetes.io
. This saves us from reimplementing a retry loop. We can configure the backoffLimit to match what we want (perhaps expose it as a configuration per job or use the default 6).
A Job keeps a history of Pods (completed/failed) which can be useful for debugging. We can also set .spec.ttlSecondsAfterFinished to auto-clean Jobs after some time if desired (Kubernetes TTL controller feature), or use the history limits to only keep a certain number of old jobs
kubernetes.io
.
A CronJob resource (CronJob is a higher-level resource that creates Jobs on a schedule) might be tempting to use for recurring jobs. For example, if a trigger is a Cron expression, we could create a CronJob in the cluster with that schedule. However, using CronJob has trade-offs:
CronJob schedules are limited to per-minute granularity (standard cron format)
kubernetes.io
, whereas Quartz cron triggers can have per-second granularity and more complex expressions. We don’t want to lose that precision or capability.
If we delegate scheduling to Kubernetes CronJob, then the Quartz scheduler (our library) would not directly manage each firing, which could complicate integration (listeners, JobDataMap changes per execution, etc., would be harder to handle).
Also, if we create CronJobs for each scheduled job, unscheduling or updating a job means manipulating CronJob objects. This could be done (the library can create/delete CronJobs), but then we rely on Kubernetes to do the actual firing, essentially making the library somewhat passive for those jobs.
Therefore, the default design is that the library will handle all scheduling itself (using its own timing mechanism and creating one Kubernetes Job at each fire time, even for recurring jobs). This ensures it fully supports Quartz features and timing. We will mention CronJob integration as a possible future optimization or option (for example, in a mode where the library is just a translator to CronJobs for long-running schedules, to survive restarts without a DB, etc.), but not in the initial implementation.
Resource Naming and Namespace: The library should allow configuration of the Kubernetes namespace in which Jobs/Pods are created. By default, it might use a namespace provided in config or if running in cluster, perhaps the same namespace as the application. Each created Job resource will need a unique name. We could generate names using a prefix and a timestamp or a random suffix. For example: quartz-job-{jobKey}-{fireTimeMillis}. Kubernetes Job names have length limits (<= 63 chars typically), so we might truncate or hash long names. We will also use labels on the Job (e.g., jobKey=<name>, jobGroup=<group> and maybe triggerKey=<triggerId>) for identification.
Cleanup: Normally, completed Jobs (and their pods) remain in the cluster until cleaned up (unless TTL is set). Our library might provide an option to automatically delete Jobs after they finish (especially one-shot jobs). But deleting immediately might remove the ability to fetch logs or see status. A compromise is to let them live for some time or keep the last N executions. Kubernetes CronJob has .spec.successfulJobsHistoryLimit and .spec.failedJobsHistoryLimit for this purpose
kubernetes.io
. For individual Jobs we create, we could set a TTL seconds after finish. We’ll make this configurable (e.g., by default, delete a job an hour after it succeeded, or never delete if the user wants to inspect manually). This is more of an operational detail but worth noting in design.
Kubernetes Integration and Execution
Using the Kubernetes Client
The library will include a Kubernetes client to communicate with the cluster. We plan to use the Fabric8 Kubernetes Client for its ease of use and popularity in the Java community
nubenetes.com
. Fabric8 allows building and creating resources like so:
KubernetesClient client = new KubernetesClientBuilder().build();
client.batch().v1().jobs().inNamespace("jobs-namespace")
      .resource(jobObject).create();
Where jobObject is a io.fabric8.kubernetes.api.model.batch.v1.Job built via a JobBuilder. The official Kubernetes Java client could also be used (it requires writing POJOs or YAML for Job specs and is a bit more verbose). The design will encapsulate client calls behind an interface KubernetesApiService so that we could swap implementations if needed. Cluster Configuration: The client will be configured to connect to the target cluster. If the application is running inside Kubernetes, the default configuration will use the in-cluster service account. If running externally (e.g., a desktop or server outside the cluster), it will use kubeconfig or an explicit config. The library could allow specifying config via a file or context. We will rely on the client’s standard config loading (Fabric8 and official clients both can auto-configure from environment). Resource Construction: When it’s time to fire a job, the library will construct a Job object. Pseudo-code for constructing a Job using Fabric8 might look like:
Job job = new JobBuilder()
    .withNewMetadata()
        .withName(jobName)
        .withLabels(labelsMap)
    .endMetadata()
    .withNewSpec()
        .withBackoffLimit(backoffLimit)
        .withNewTemplate()
            .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                    .withName("job-runner")
                    .withImage(jobImage)
                    .withArgs(JobRunnerMainClass, "--jobClass", jobClassName, "--jobData", jobDataJson)
                .endContainer()
            .endSpec()
        .endTemplate()
    .endSpec()
    .build();
client.batch().v1().jobs().inNamespace(ns).create(job);
We ensure restartPolicy: Never (as noted, to let the Job controller handle retries via new pods rather than restarting in-place). We use the configured jobImage. The JobRunnerMainClass might be a constant defined by the library (for example, if the container’s entrypoint is not already the job runner, we might specify the main class if the image’s entrypoint is java). If the image is built such that its default entrypoint is our JobRunner, we could simply pass arguments for jobClass and data. This depends on how users build their images. Tracking Pod Status: Once the Job is created, Kubernetes will schedule a Pod. We have a few ways to track its progress:
Polling: The scheduler can periodically poll the Job status (using client API to get the Job resource and check .status.succeeded or .status.failed count). Once succeeded or failed, we consider the execution done.
Watching: Fabric8 client supports watch which can notify when a resource changes. We could set up a watch on either the specific Job or on Pods with a certain label. For simplicity, polling every few seconds might be enough. The overhead is minimal if jobs are not extremely frequent.
When the Job completes, if .status.succeeded == 1, it means the job ran successfully. If .status.failed > 0 and backoffLimit reached, it means it ultimately failed. We can then collect logs (if not already streamed) and mark the Quartz job execution as finished. Log Streaming Implementation: If log streaming is enabled, right after creating the Job, the scheduler can attempt to attach to the Pod’s logs. There is a bit of a race (the Pod might not be running yet). We can implement a small wait: watch for the Pod to be assigned/run, then start a log stream. Fabric8 allows something like:
LogWatch watch = client.pods().inNamespace(ns).withName(podName)
    .tailingLines(0)
    .watchLog(System.out);
This would pipe the pod’s log to the system output. We could also integrate it with the application logging (e.g., capture the lines and log via slf4j). If using the official client, it provides a readNamespacedPodLog call and also supports websockets for stream. We will hide these details behind a LogStreamer component. It will also handle termination: once the pod is finished (and logs stream ends), it should close resources. We need to ensure that streaming a log doesn’t block the main scheduler thread. It would be run in a separate thread or use async callback. In design, we'll dedicate either one thread per streaming (for each running job) or use a common thread pool for log streams if necessary. Since jobs can be many, maybe a thread per job is not scalable if thousands of jobs run simultaneously. An alternative is not to stream extremely long-running jobs but rather fetch logs periodically. However, for simplicity, we assume typical use where jobs are moderately sized and a streaming thread per job is acceptable. This is configurable, and users can disable streaming to avoid the overhead. Security and Permissions: If the application runs in Kubernetes, its service account must have permission to create and monitor Jobs in the target namespace. This implies some RBAC setup (allow create/get/watch on jobs/pods). This will be documented. If running externally, the kubeconfig user must have these permissions. This is not a code design issue but a deployment consideration.
Job Execution Environment (Container)
Now focusing on the Job Runner container: this is the runtime that actually executes the Job. We envision a couple of ways this could be set up:
Same Application Image Approach: The user's application itself might be packaged as a container image which includes both the scheduling library and the job classes. In such a scenario, one could use the same image to run jobs by just overriding the command. For example, the main application might normally start a Spring Boot app (including the scheduler). But for job execution, we could run the same image with a different entrypoint/command to run a specific job and exit. The benefit is that all classes (including the user’s Job implementations) are present. The drawback is the image might be large and includes unnecessary parts for a single job run. However, it ensures consistency.
Dedicated Job Runner Image: We create a separate lightweight image whose purpose is to run Quartz jobs. It might include the Quartz-Kubernetes library (or a portion of it) and perhaps requires the user’s job classes to be available. How to get the user’s classes into this image? They could be packaged as a dependency jar or the user extends a base image by adding their jar. For instance, we could provide a base image quartz-k8s-runner that contains our runner logic, and the user builds an image FROM that, adding their application jar to /app.jar. The runner would know to load /app.jar. This is more of a deployment pattern than library code, but we can document it.
Remote Class Loading: This would be an advanced approach where the job code is not pre-bundled in the image, but fetched at runtime (e.g., from a network location or ConfigMap). This is likely too complex and out-of-scope initially, but could be considered for extensibility (for example, if the library could upload the job bytecode to a ConfigMap or volume that the pod mounts). We will not pursue this in the initial design due to complexity and security concerns.
Given typical usage, the simplest assumption is that the job classes are in the image. So in code, when the container starts, it has everything needed. JobRunner Implementation: Inside the container, the entrypoint could be something like: java com.mycompany.QuartzJobRunner --jobClass com.example.jobs.MyJob --jobData '{"foo":"bar"}'. The QuartzJobRunner class (provided by our library or the base image) does roughly the following:
Parses arguments (gets the job class name and the data JSON).
Initializes a minimal Quartz Scheduler context (or at least constructs a JobExecutionContext object). It likely doesn’t have a real scheduler, but it can create a dummy Scheduler reference or a simple implementation if needed by the job (e.g., if the job calls context.getScheduler() for something, maybe we can provide limited functionality or stub).
Instantiates the job class using reflection (Class.forName(jobClassName).newInstance() or using an appropriate class loader).
Populates a JobDataMap from the JSON for the context.
Calls jobInstance.execute(jobExecutionContext). Surround this call with try-catch to capture any exception.
If the job throws an exception, the runner can log it and set an exit code (non-zero). The Kubernetes Job will then mark the Pod as failed. (We might also output the stack trace to stdout so it appears in logs).
If the job completes successfully, exit with code 0. (We might still output a completion message).
We could also support PersistJobDataAfterExecution and JobDataMap result passing: Quartz has an annotation @PersistJobDataAfterExecution which indicates that changes to the JobDataMap by the job should be persisted for next time. Since our executions are stateless (each in a new process), we’d need to send updated data back. This is complex – one approach is to output a special JSON to stdout that the scheduler can parse. That is probably overkill; instead, we might not fully support automatically capturing changed data in this version. We can note this limitation.
Container Resource Requirements: The design should consider that each job pod might need specific resources (CPU, memory). The library can allow configuration of default resources for job pods (e.g., via a Kubernetes ResourceRequirements setting). Perhaps users can specify in config: default CPU request/limit, memory request/limit for job containers. Or if different jobs have different needs, they might choose to specify an alternate image or have the job itself request certain resources. Since Quartz’s API doesn’t have a notion of resource requirements, this could be handled via job-specific data or configuration mapping (for example, in a properties file mapping job names to resource settings). For now, we will mention that the library could provide a config hook for a Pod template or resource limits that apply to all jobs, and possibly extended to per-job settings via job metadata.
Failure Handling and Retries
Using Kubernetes Job’s retry mechanism was already described (backoffLimit, etc.). To integrate this with Quartz semantics:
If a job fails and Kubernetes retries it successfully on second attempt, from the perspective of the Quartz scheduler, should this be reported as a failure or success? In Quartz, if a job throws an exception and is not caught, Quartz will consider that execution failed and will invoke listeners’ jobWasExecuted with an exception, and possibly refire immediately if it was a misfire or wait for next trigger. There is no built-in retry-on-failure in Quartz except via trigger misfire instructions or scheduling another job.
In our case, Kubernetes is handling the retry transparently. We might decide that a job execution is only considered failed if all retries fail (i.e., the Kubernetes Job as a whole has failed). This makes sense because the user likely wants the job to eventually succeed if possible. So we will treat the Kubernetes Job’s final result as the outcome of that trigger firing. If it succeeded on a retry, we will treat it as success (and maybe log that it succeeded after N retries). If it exhausts retries and fails, we treat it as a job execution failure.
We can expose configuration for backoffLimit. A global default (6 as per Kubernetes default
kubernetes.io
) could be used, but the user might want to override per job. Perhaps they can specify in the JobDataMap something like "k8sBackoffLimit": 0 for no retries, or in a global config map listing job names and their retry counts. Initially, a simpler approach: have a global default and allow override via a property in JobDetail or Trigger (for example, an extended interface KubernetesTrigger with a method getBackoffLimit() or just use JobDataMap).
What about backoff timing? Kubernetes handles exponential backoff delays (doubling up to 6 minutes max)
kubernetes.io
. We probably don't need to modify that, although Kubernetes 1.21+ allows setting a podFailurePolicy to fine-tune which failures count, but that's advanced usage.
Failure reporting: If a job ultimately fails, the library should mark that trigger execution as failed. In Quartz, if a job throws an exception, by default it just logs it and continues (unless the exception is a special type that can refire immediately). We can simulate this by logging the failure in the application logs. If the user has a JobListener, call jobWasExecuted with the exception in the JobExecutionContext. The actual exception object may not be easily transferred (different process), but we could wrap the exception message from the container logs into a new Exception type (e.g., JobExecutionException with message "Job failed with exit code X. See logs for details.").
For one-shot jobs, if failed, there's no next trigger unless user scheduled them to repeat or handles it manually. For recurring jobs, the failure of one occurrence does not necessarily unschedule future occurrences (Quartz would still schedule the next occurrence normally, unless the job is non-recoverable and the scheduler was shutdown mid-run, etc.). We will follow that: even if one run fails, the Cron trigger will still schedule the next run as long as the schedule is still active.
Configuration Examples
Below are examples demonstrating how a developer might configure and use the Quartz-Kubernetes Scheduler library in code and configuration files:
Library Initialization
Suppose we provide a KubernetesSchedulerFactory similar to Quartz’s StdSchedulerFactory. Usage might look like:
import com.mycompany.quartzk8s.KubernetesSchedulerFactory;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

public class SchedulerApp {
    public static void main(String[] args) throws Exception {
        // Create scheduler factory and configure
        KubernetesSchedulerFactory factory = new KubernetesSchedulerFactory();
        factory.setKubernetesNamespace("jobs");       // Namespace to run jobs in
        factory.setJobContainerImage("mycompany/app-jobs:latest"); // Image that has job classes
        factory.setMaxConcurrentJobs(10);             // (Optional) throttle concurrency
        factory.enableLogStreaming(true);             // Stream logs from pods to console

        // Optionally configure persistence
        factory.useJdbcStore("jdbc:postgresql://db:5432/quartz", "user", "pass");
        // Or: factory.useKubernetesCRDStore();

        // Get Scheduler instance
        Scheduler scheduler = factory.getScheduler();
        scheduler.start();

        // Define a job and trigger (same as Quartz usage)
        JobDetail job = newJob(MyJob.class)
            .withIdentity("myJob", "group1")
            .usingJobData("someKey", "someValue")  // JobDataMap entry
            .build();

        Trigger trigger = newTrigger()
            .withIdentity("myTrigger", "group1")
            .startNow()
            .withSchedule(simpleSchedule()
                .withIntervalInSeconds(30)
                .repeatForever())
            .build();

        // Schedule the job
        scheduler.scheduleJob(job, trigger);
    }
}
In a Spring Boot application, one could define a bean for Scheduler via a factory as well, pointing to this custom implementation, with similar properties in application.yaml (if we integrate configuration properties).
Kubernetes Cluster Preparation
The cluster needs to know about the image mycompany/app-jobs:latest. This image should include MyJob.class and any other job classes used. For example, the Dockerfile might look like:
FROM eclipse-temurin:17-jre-alpine
COPY app.jar /app.jar
# Entry point can be a shell that waits for args to run jobs
ENTRYPOINT ["java", "-cp", "/app.jar", "com.mycompany.quartzk8s.JobRunner"]
In this case, our library’s JobRunner class is the entrypoint. It expects environment variables or command-line parameters for the job to run. The scheduler, when creating a Job, will set those parameters (--jobClass, etc.) as shown earlier. Alternatively, if the entrypoint itself is flexible, we could override the entrypoint in the Kubernetes Job spec to run the JobRunner class with arguments. The user must also ensure RBAC is set so that the scheduler can create Jobs in the "jobs" namespace. For example, create a Role/ClusterRole and bind it to the service account of the app.
Running and Logs
When the above code schedules MyJob with a trigger, the library will output something like (log messages for illustration):
INFO  - Scheduled job 'group1.myJob' with trigger 'group1.myTrigger' to run at 2025-06-05T13:00:00Z
INFO  - Creating Kubernetes Job: quartz-job-myJob-1622907600000 in namespace 'jobs'
INFO  - Job Pod quartz-job-myJob-...-abcd starting... (Image: mycompany/app-jobs:latest)
If log streaming is enabled, as soon as the Pod starts, you might see the output from inside the job:
[Job: group1.myJob] Starting execution in Kubernetes Pod (Node aks-node-1)...
[Job: group1.myJob] Doing work... 
[Job: group1.myJob] Completed successfully.
(These messages would be coming from within the container’s stdout, prefix added by the JobRunner for clarity). After completion:
INFO - Job 'group1.myJob' [trigger 'group1.myTrigger'] completed at 13:00:05 (duration 5s)
If something went wrong, e.g., an exception, the log might show:
[Job: group1.myJob] ERROR: Exception in job execution: java.lang.RuntimeException: ...
... (stack trace) ...
ERROR - Job 'group1.myJob' failed after 3 retries, marked as FAILED.
And the scheduler could throw a JobExecutionException or at least log that the job execution failed.
Persistent Store Config
If using the JDBC store for persistence, the library might reuse Quartz’s table schema (so an existing Quartz database could be used). In the above snippet, factory.useJdbcStore(jdbcUrl, user, pass) would configure the scheduler to store job details and triggers in that DB. This means if the application restarts, it can retrieve the scheduled jobs from the DB and resume (possibly firing misfired triggers). It also means multiple instances could be running and coordinate via DB locks (just like Quartz clustering
quartz-scheduler.org
). In clustered mode, we'd ensure only one instance launches a given job at a time. Achieving that likely means leveraging the same locking queries Quartz uses, or implementing a simpler "leadership" approach. If using a Kubernetes CRD store, we might create a CRD like:
apiVersion: mycompany.com/v1
kind: ScheduledJob
metadata:
  name: group1.myJob
spec:
  schedule: "*/30 * * * * *"    # cron expression (Quartz style with seconds)
  image: "mycompany/app-jobs:latest"
  jobClass: "com.myapp.MyJob"
  data: {"someKey": "someValue"}
  suspend: false
status:
  lastScheduleTime: "2025-06-05T13:00:00Z"
  lastStatus: "Succeeded"
The library would create such CRDs for each scheduled job. However, unless there’s a controller in the cluster to act on them, they serve mainly as a persistent record. Our scheduler can on startup list these CRDs and reconstruct the schedule. (Future extension: a Kubernetes Operator could watch ScheduledJob CRDs and create Job objects on schedule, offloading the scheduling from the app entirely. But that would require writing a controller. For now, the Java app itself is the “controller”.)
Extensibility and Future Considerations
The design above covers the core functionality, but we have kept in mind several extension points and future improvements:
Pluggable Job Store: As noted, we plan to allow different persistence strategies. The library could define a JobStore interface (similar to Quartz’s JobStore SPI). Initially an in-memory store is default. A JDBC implementation can be added (possibly reusing Quartz’s table schema for compatibility, or a custom simplified schema). The CRD-based store is another option. The library should make it easy to plug in a new store without changing the core scheduling logic. This is useful if, for example, someone wants to use a distributed cache or another system to store schedules.
High Availability & Clustering: In critical systems, you might run multiple instances of the scheduler for failover. Quartz DB clustering would work if we implement a JDBC store with locks
quartz-scheduler.org
. Another approach native to Kubernetes is leader election. We could implement a leader election (e.g., using Kubernetes Lock/Lease object or a library) so that out of N instances, one is the active scheduler and others are standby. If the leader dies, a standby takes over scheduling. During failover, some triggers might be missed, so combining this with persistent store and misfire handling is important. This is a complex area but the design is mindful of it. Initially, we might document that only one scheduler instance should be active, but provide guidelines for HA via external tools or DB clustering. Eventually, adding built-in leader election (especially if using the Kubernetes API anyway) is a nice addition.
Kubernetes CronJob Integration: As discussed, an optional optimization could be to use CronJob for long-running recurring schedules. For example, if a job is scheduled to run every day at 6am via a Cron trigger, the library could (if configured to do so) create a CronJob resource in Kubernetes instead of managing it in-process. This would mean even if the app goes down, Kubernetes would still launch the job at 6am. The downside is the loss of second-level precision and Quartz-specific behavior. We could make this an opt-in feature per trigger (e.g., if trigger is a daily/ hourly cron, allow offload). The design easily allows adding this: the scheduleJob method can detect if a trigger is Cron and an option is enabled, then create a CronJob resource. We’d then not schedule it internally at all. We must then also watch that CronJob for events if we want to propagate job execution events back (or perhaps treat it as fire-and-forget, simply logging that the job is now under Kubernetes control). This is left for future work due to the complexity of synchronizing state.
Enhanced Logging and Monitoring: We focused on streaming logs to the console, but extensibility can include integrating with logging frameworks. For instance, the library could allow plugging a custom log handler to receive pod log lines and log them with the application’s logger (with MDC data like job name). We can also integrate metrics: track how many jobs succeeded/failed, execution durations, etc. These could be exposed via JMX or published to a monitoring system. This would help in large deployments to observe scheduler behavior.
Supporting Complex Pod Templates: Some jobs might require more than just a single container with the job code. Perhaps they need sidecar containers (for example, a job that needs a local PostgreSQL to run tests, etc.), or volumes mounted (to access a dataset), or custom environment (config maps, secrets). Our current design uses a simple generated pod spec. We could allow a pod template to be specified for jobs. For example, a YAML snippet or a reference to a Kubernetes PodTemplate resource that the library can use as a base. The library would then inject the necessary job-specific overrides (like container image tag or job class args) into that template. This gives advanced users full control over the pod environment. This is an advanced feature; initially, we might just support basic customization (image, resources) via config properties. But the architecture can be extended to accept a PodSpec object or template per job type.
Security and Isolation: Running code in containers adds security concerns (which are also benefits if done right). We might allow configuring security context for pods (run as a certain user, etc.), or use PodSecurityPolicies (or Pod Security Standards). Also, if multiple jobs run in parallel, they are isolated by containers, which is good. But note that if they all pull from the same image, the image must have all needed code. If a user wanted to run two very different types of tasks on the same scheduler, they might even use different images. This could be achieved by adding an attribute in JobDataMap like "k8sImage": "custom/image:tag" which the scheduler will use for that job execution instead of the default. We plan to support such per-job overrides for image (and potentially other settings like resources). This provides flexibility to use the right container for the right job.
Result Handling: Sometimes jobs produce a result that the application needs. Quartz usually handles this by the job itself storing results somewhere (since execute() has no return and one cannot directly get a result except via job data or listeners). In our scenario, the job could, for example, save results to a database or send a message. But if needed, we could implement a way for the JobRunner to capture a result value and communicate it back. For instance, if we standardize that the job can put a result in the JobDataMap under a known key by end of execution, the JobRunner could serialize that and perhaps annotate the Kubernetes Job or write to a temporary file that the scheduler reads. This is complicated and would introduce tight coupling, so it's not a priority for now. We assume jobs handle their outputs externally as needed. Our focus is on scheduling and execution.
Testing and Local Execution Mode: It might be useful to have a mode where the scheduler can execute jobs locally (for unit testing or development without a cluster). We could allow the Kubernetes dispatcher to have a "local" implementation that simply invokes the job in-process. This could be triggered by configuration (e.g., if kubernetes.enabled=false, then it falls back to a local mode). This would let developers run jobs without needing a cluster, albeit without isolation. Since our library is meant to imitate Quartz, one could also just use Quartz locally, but having a single code path is convenient. This is a nice-to-have for developer experience.
Documentation and Examples: As part of extensibility, ensuring we have clear guides for how to build the container image, how to configure RBAC, how to set up persistence, etc., is crucial. This isn't a code feature, but the design should include delivering example configs (Kubernetes YAMLs, Dockerfiles, etc.) in the repository so users can get started easily.
Kubernetes API Version Upgrades: The library should be maintained to support newer Kubernetes versions. For instance, in newer versions, CronJob moved to batch/v1 (which we target), new fields like timeZone were introduced for CronJobs
kubernetes.io
, etc. We must keep the client library up-to-date and consider version compatibility. Using fabric8, which often supports multiple k8s versions, will help. Alternatively, generating our own client from the OpenAPI could tie to a specific version. We might choose to support Kubernetes 1.20+ at minimum, given CronJob and Job features are quite stable by then.
In summary, the design provides a robust starting point for a Quartz-compatible scheduler that delegates execution to Kubernetes. By keeping the same API, we make it easy for existing projects to adopt cloud-native job execution without rewriting their scheduling logic. At the same time, we leverage Kubernetes features (retries, cron, scaling, isolation) to run jobs more reliably and flexibly. The architecture is modular, allowing for future enhancements like different persistence layers, deeper Kubernetes integration (operators), and custom execution environments. With this design document as a foundation, the next step would be to implement the components, starting with the core scheduling logic, the Kubernetes dispatcher, and a simple job runner, then iteratively adding features like persistence and log streaming. The document is detailed enough to guide implementation and can be included in the repository to help developers (or AI code generators) understand the intended structure and behavior of the system.
Citations
Tutorial 2

https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-02.html
Favicon
Client Libraries for Kubernetes - Go client, Python, Fabric8, JKube & Java Operator SDK - Nubenetes

https://nubenetes.com/kubernetes-client-libraries/
Favicon
Jobs | Kubernetes

https://kubernetes.io/docs/concepts/workloads/controllers/job/
Favicon
Jobs | Kubernetes

https://kubernetes.io/docs/concepts/workloads/controllers/job/
Quartz Job Scheduler | Quartz website generated from quartz-scheduler.org-site repository (ALL PRs to that repo please)

https://www.quartz-scheduler.org/documentation/quartz-1.8.6/configuration/ConfigJDBCJobStoreClustering.html
Favicon
Custom Resources | Kubernetes

https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/
DisallowConcurrentExecution (Quartz Parent POM 2.1.7 API)

https://www.quartz-scheduler.org/api/2.1.7/org/quartz/DisallowConcurrentExecution.html
Favicon
CronJob | Kubernetes

https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
Favicon
CronJob | Kubernetes

https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
Favicon
Running Automated Tasks with a CronJob | Kubernetes

https://kubernetes.io/docs/tasks/job/automated-tasks-with-cron-jobs/
Favicon
Jobs | Kubernetes

https://kubernetes.io/docs/concepts/workloads/controllers/job/
Favicon
CronJob | Kubernetes

https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/
All Sources
quartz-scheduler
Favicon
nubenetes
Favicon
kubernetes

## Cluster Requirements
See [docs/cluster-setup.md](docs/cluster-setup.md) for RBAC roles and cluster preparation steps.
