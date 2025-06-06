package com.quartzkube.core;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.util.Yaml;

/**
 * Alternative implementation of {@link KubernetesApiService} using the
 * official Kubernetes Java client.
 */
public class OfficialKubernetesApiService implements KubernetesApiService {
    private final BatchV1Api batchApi;
    private final CoreV1Api coreApi;
    private final String namespace;

    public OfficialKubernetesApiService(ApiClient client, String namespace) {
        this.batchApi = new BatchV1Api(client);
        this.coreApi = new CoreV1Api(client);
        this.namespace = namespace;
    }

    @Override
    public void create(String manifest) throws Exception {
        Object obj = Yaml.load(manifest);
        if (obj instanceof V1Job job) {
            batchApi.createNamespacedJob(namespace, job).execute();
        } else if (obj instanceof V1CronJob cron) {
            batchApi.createNamespacedCronJob(namespace, cron).execute();
        } else {
            throw new IllegalArgumentException("Unsupported manifest kind: " + obj.getClass());
        }
    }

    @Override
    public String readPodLog(String podName) throws Exception {
        return coreApi.readNamespacedPodLog(podName, namespace).execute();
    }

    @Override
    public Watch watchPod(String podName, Watcher<Pod> watcher) {
        final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread t = new Thread(() -> {
            try {
                while (running.get()) {
                    V1Pod v1pod = coreApi.readNamespacedPodStatus(podName, namespace).execute();
                    String yaml = Yaml.dump(v1pod);
                    Pod pod = io.fabric8.kubernetes.client.utils.Serialization.unmarshal(yaml, Pod.class);
                    watcher.eventReceived(Watcher.Action.MODIFIED, pod);
                    String phase = v1pod.getStatus() != null ? v1pod.getStatus().getPhase() : null;
                    if ("Succeeded".equals(phase) || "Failed".equals(phase)) {
                        running.set(false);
                        watcher.onClose(null);
                        break;
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                watcher.onClose(new WatcherException(e.getMessage(), e));
            }
        });
        t.setDaemon(true);
        t.start();
        return () -> running.set(false);
    }
}
