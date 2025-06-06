package com.quartzkube.core;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.api.model.Pod;

/**
 * Default implementation of {@link KubernetesApiService} using the Fabric8 client.
 */
public class Fabric8KubernetesApiService implements KubernetesApiService {
    private final KubernetesClient client;
    private final String namespace;

    public Fabric8KubernetesApiService(KubernetesClient client, String namespace) {
        this.client = client;
        this.namespace = namespace;
    }

    @Override
    public void create(String manifest) throws Exception {
        client.load(new java.io.ByteArrayInputStream(manifest.getBytes())).create();
    }

    @Override
    public String readPodLog(String podName) throws Exception {
        return client.pods().inNamespace(namespace).withName(podName).getLog();
    }

    @Override
    public Watch watchPod(String podName, Watcher<Pod> watcher) {
        return client.pods().inNamespace(namespace).withName(podName).watch(watcher);
    }
}
