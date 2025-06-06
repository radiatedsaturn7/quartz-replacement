# TODO List
- [x] Initialize project skeleton with Maven build and stub classes (QuartzKubeScheduler, KubeJobDispatcher, JobTemplateBuilder, JobRunner, example HelloWorldJob).
- [x] Implement basic QuartzKubeScheduler start and scheduleJob logic (in-memory schedule only).
- [x] Flesh out KubeJobDispatcher to create Kubernetes Job manifests (no real API calls).
- [x] Add JobRunner logic to load and run a job class by name via reflection.
- [x] Provide simple tests for scheduler interactions.
- [x] Allow configuring the container image via an environment variable in JobTemplateBuilder.
- [x] Add a local execution mode to KubeJobDispatcher for testing without Kubernetes.
- [x] Stream job logs to stdout (placeholder implementation).
- [x] Add example Kubernetes YAML files for reference.
- [x] Integrate with the Kubernetes API client to actually create Job resources.
- [x] Support per-job container image overrides via JobDataMap.
- [x] Optional CronJob integration for recurring schedules.
 - [x] Implement cleanup/TTL logic for completed Kubernetes Jobs.
 - [x] Provide a sample Dockerfile for building the JobRunner image.
- [x] Allow configuration of CPU and memory limits for dispatched jobs.
- [x] Enable setting the target namespace for created Kubernetes resources.
- [x] Implement retry/backoff handling using Kubernetes Job settings.
- [x] Implement @DisallowConcurrentExecution serialization logic.
- [x] Add real log streaming from Kubernetes pods.
- [x] Enforce a global limit on concurrent job dispatches.
- [x] Document RBAC setup and cluster requirements.
- [x] Expose basic metrics for job execution counts.
- [x] Allow configuring pod security context in job templates.
- [x] Support custom environment variables per job.
- [x] Support custom job templates provided via external YAML files.
- [x] Provide a simple persistence mechanism using Kubernetes CRDs.
- [x] Support CronJob timeZone configuration.
- [x] Write DOC.md with user-friendly usage guide and migration examples.

- [x] Add JobResultListener interface and hook into dispatcher to report job success or failure.
- [x] Document JobResultListener usage in DOC.md.

- [x] Support custom labels and annotations per job for additional metadata.
- [x] Allow specifying pod affinity/anti-affinity rules.
- [x] Expose Prometheus metrics endpoint for metrics.
- [x] Add integration tests using KinD or Minikube to exercise Kubernetes interactions.
- [x] Add GitHub Actions workflow for Maven build and tests.

## Next Steps
- [x] Support scheduling via `JobDetail` and `Trigger` objects for better Quartz compatibility.
- [x] Update DOC.md migration section to demonstrate using `JobDetail` and `Trigger` unchanged.

# New Tasks
- [x] Create `QuartzKubeSchedulerFactory` that loads settings from environment variables, system properties, or a properties file.
- [x] Support hot-reloading the properties file in `QuartzKubeSchedulerFactory`.
- [x] Provide a Helm chart for deploying HelloWorldJob on Minikube.
- [x] Document Helm chart usage in DOC.md.
- [x] Document scheduler factory usage in DOC.md.

## Upcoming Work
- [x] Honor Quartz `Trigger` types when scheduling jobs: use `CronTrigger` to dispatch CronJobs and `SimpleTrigger` start times for delayed jobs.
- [x] Document the new trigger support in DOC.md.

## Future Work
- [x] Create release workflow to publish artifacts to GitHub Packages when tags are pushed.
 - [x] Expand DOC.md with more migration examples and troubleshooting tips.

- [x] Move README.md to DESIGN.md.
- [x] Create new README with Hello World instructions and example.

# Additional Tasks
- [x] Implement JDBC-backed JobStore for persistent scheduling and clustering.
- [x] Add Quartz JobListener and TriggerListener support to propagate job events.
- [x] Integrate slf4j logging by providing a pluggable log handler for job output.
- [x] Replace manual HttpClient calls with the Fabric8 Kubernetes client and add watch-based job monitoring.

