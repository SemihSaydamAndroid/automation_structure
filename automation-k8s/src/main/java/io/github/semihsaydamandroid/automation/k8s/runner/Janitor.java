package io.github.semihsaydamandroid.automation.k8s.runner;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.semihsaydamandroid.automation.core.util.Names;
import io.github.semihsaydamandroid.automation.k8s.support.ResourceLabels;

/**
 * Deletes what the framework created for one owner (pods, distributed load Jobs and their plan
 * ConfigMaps), e.g. after an interrupted local run.
 */
public final class Janitor {

    private static final Logger LOG = LoggerFactory.getLogger(Janitor.class);

    private final KubernetesClient client;

    public Janitor(KubernetesClient client) {
        this.client = client;
    }

    public int cleanup(String namespace, String owner) {
        String ownerLabel = Names.toDnsLabel(owner);
        List<Job> jobs = client.batch().v1().jobs().inNamespace(namespace)
                .withLabel(ResourceLabels.MANAGED_BY, ResourceLabels.MANAGED_BY_VALUE)
                .withLabel(ResourceLabels.OWNER, ownerLabel)
                .list().getItems();
        for (Job job : jobs) {
            client.batch().v1().jobs().inNamespace(namespace).withName(job.getMetadata().getName())
                    .withPropagationPolicy(DeletionPropagation.BACKGROUND).delete();
            LOG.info("Deleted job {}/{}", namespace, job.getMetadata().getName());
        }
        List<ConfigMap> configMaps = client.configMaps().inNamespace(namespace)
                .withLabel(ResourceLabels.MANAGED_BY, ResourceLabels.MANAGED_BY_VALUE)
                .withLabel(ResourceLabels.OWNER, ownerLabel)
                .list().getItems();
        for (ConfigMap configMap : configMaps) {
            client.configMaps().inNamespace(namespace).withName(configMap.getMetadata().getName()).delete();
        }
        List<Pod> pods = client.pods().inNamespace(namespace)
                .withLabel(ResourceLabels.MANAGED_BY, ResourceLabels.MANAGED_BY_VALUE)
                .withLabel(ResourceLabels.OWNER, ownerLabel)
                .list().getItems();
        for (Pod pod : pods) {
            client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).withGracePeriod(0).delete();
            LOG.info("Deleted pod {}/{}", namespace, pod.getMetadata().getName());
        }
        return pods.size() + jobs.size() + configMaps.size();
    }
}
