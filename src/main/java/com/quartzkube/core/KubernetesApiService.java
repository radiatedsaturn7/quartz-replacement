package com.quartzkube.core;

import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.api.model.Pod;

/**
 * Simple abstraction over Kubernetes client operations to allow different
 * implementations or mocking in tests.
 */
public interface KubernetesApiService {
    /** Create resources defined by the provided manifest YAML or JSON. */
    void create(String manifest) throws Exception;

    /** Fetch logs for the given pod in the configured namespace. */
    String readPodLog(String podName) throws Exception;

    /** Watch the specified pod and forward events to the given watcher. */
    Watch watchPod(String podName, Watcher<Pod> watcher);
}
