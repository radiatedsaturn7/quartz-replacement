# QuartzKube Hello World Guide

**TL;DR**

```bash
mvn package
minikube start
minikube image load quartz-job-runner:latest
kubectl apply -f examples/job-example.yaml
kubectl logs job/sample-quartzkube-job -f
```

## Requirements

Install Java 17, Maven, and Minikube so you can build and run the example.

### Windows (Chocolatey)

```powershell
choco install openjdk17 maven minikube
```

### Linux (Debian/Ubuntu)

```bash
sudo apt-get install openjdk-17-jdk maven
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
```

### macOS (Homebrew)

```bash
brew install openjdk@17 maven minikube
```

Or test locally without Kubernetes:

```bash
java -cp target/quartzkube-*.jar com.quartzkube.examples.HelloWorld
```

This project packages a minimal example of using **QuartzKube**, a scheduler that runs your jobs as Kubernetes `Job` objects. If you can run `java`, you can try it.

## 1. Prerequisites

- Java 17 or newer
- Maven
- A local Kubernetes cluster such as [Minikube](https://minikube.sigs.k8s.io/docs/) (kubectl should connect to it)

## 2. Build the Project

```bash
mvn package
```

This compiles the scheduler and builds a small `job-runner` Docker image.

## 3. Start Minikube

```bash
minikube start
```

Ensure `kubectl get nodes` works before continuing.

## 4. Load the Job Runner Image

With Minikube you can load the image directly:

```bash
minikube image load quartz-job-runner:latest
```

## 5. Run the Example Job

Apply the provided YAML to launch a Kubernetes Job that runs our sample class:

```bash
kubectl apply -f examples/job-example.yaml
```

Watch the pod logs to see the output:

```bash
kubectl logs job/sample-quartzkube-job -f
```

You should see `Hello from HelloWorldJob` printed.

## 6. Deploy Using Helm

A simple Helm chart is provided in `charts/hello-world`. Install it with:

```bash
helm install my-job ./charts/hello-world
```

Then watch the job logs:

```bash
kubectl logs job/my-job -f
```

## 7. How It Works

The build produces a Docker image containing your classes and a small `JobRunner`. The scheduler dispatches a Kubernetes Job that sets the `JOB_CLASS` environment variable. When the pod starts, the JobRunner reads this variable and runs the specified class. The scheduler and job pods use the same image.

## 8. Running Locally Without Kubernetes

To test without a cluster, run the `HelloWorld` class directly:

```bash
java -cp target/quartzkube-*.jar com.quartzkube.examples.HelloWorld
```

This uses the in-memory mode and simply prints `Hello from HelloWorldJob`.

## Files

- `examples/job-example.yaml` – Kubernetes Job manifest referencing `HelloWorldJob`.
- `src/main/java/com/quartzkube/examples/HelloWorldJob.java` – simple job class.
- `src/main/java/com/quartzkube/examples/HelloWorld.java` – small runner for local testing.
- `charts/hello-world` – Helm chart for the example job.

Enjoy!
