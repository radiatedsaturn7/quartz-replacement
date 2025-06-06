package com.quartzkube.integration;

import com.quartzkube.core.JobTemplateBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

public class KubernetesIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_K8S_IT", matches = "true")
    public void testJobRunsInCluster() throws Exception {
        K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.27.4-k3s1"));
        k3s.start();
        KubernetesClient client = new KubernetesClientBuilder()
                .withConfig(k3s.getKubeConfigYaml())
                .build();

        JobTemplateBuilder builder = new JobTemplateBuilder("busybox");
        String yaml = builder.buildTemplate("com.example.DummyJob");
        Job job = client.batch().v1().jobs()
                .inNamespace("default")
                .load(new java.io.ByteArrayInputStream(yaml.getBytes()))
                .create();

        assertNotNull(client.batch().v1().jobs().withName(job.getMetadata().getName()).get());
        k3s.stop();
    }
}
