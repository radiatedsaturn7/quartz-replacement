package com.quartzkube.core;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpec;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Minimal leader election implementation using Kubernetes Lease objects.
 */
public class LeaderElection {
    private final KubernetesClient client;
    private final String namespace;
    private final String leaseName;
    private final String identity = UUID.randomUUID().toString();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean leader;
    private final long leaseDurationMillis = 10000; // 10s

    public LeaderElection(String apiUrl, String namespace, String leaseName) {
        Config cfg = new ConfigBuilder()
                .withMasterUrl(apiUrl)
                .withNamespace(namespace)
                .withTrustCerts(true)
                .build();
        this.client = new DefaultKubernetesClient(cfg);
        this.namespace = namespace;
        this.leaseName = leaseName;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::tryAcquire, 0, leaseDurationMillis / 2, TimeUnit.MILLISECONDS);
    }

    public boolean isLeader() {
        return leader;
    }

    private void tryAcquire() {
        try {
            Resource<Lease> res = client.leases().inNamespace(namespace).withName(leaseName);
            Lease lease = res.get();
            long now = System.currentTimeMillis();
            if (lease == null) {
                lease = new LeaseBuilder()
                        .withMetadata(new ObjectMeta())
                        .editOrNewMetadata().withName(leaseName).endMetadata()
                        .withNewSpec()
                        .withHolderIdentity(identity)
                        .withRenewTime(java.time.ZonedDateTime.now())
                        .withLeaseDurationSeconds((int) (leaseDurationMillis / 1000))
                        .endSpec()
                        .build();
                res.create(lease);
                leader = true;
                return;
            }
            LeaseSpec spec = lease.getSpec();
            long renew = spec.getRenewTime() != null ? spec.getRenewTime().toInstant().toEpochMilli() : 0;
            long expire = renew + spec.getLeaseDurationSeconds() * 1000L;
            if (identity.equals(spec.getHolderIdentity()) || expire < now) {
                spec.setHolderIdentity(identity);
                spec.setRenewTime(java.time.ZonedDateTime.now());
                if (spec.getLeaseDurationSeconds() == null) {
                    spec.setLeaseDurationSeconds((int) (leaseDurationMillis / 1000));
                }
                res.replace(lease);
                leader = true;
            } else {
                leader = false;
            }
        } catch (Exception e) {
            leader = false;
        }
    }

    public void stop() {
        executor.shutdownNow();
    }
}

