package io.github.semihsaydamandroid.automation.k8s.runner;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.semihsaydamandroid.automation.core.util.Names;
import io.github.semihsaydamandroid.automation.k8s.support.ResourceLabels;

/** Deletes pods created by the framework for one owner, e.g. after an interrupted local run. */
public final class Janitor {

    private static final Logger LOG = LoggerFactory.getLogger(Janitor.class);

    private final KubernetesClient client;

    public Janitor(KubernetesClient client) {
        this.client = client;
    }

    public int cleanup(String namespace, String owner) {
        List<Pod> pods = client.pods().inNamespace(namespace)
                .withLabel(ResourceLabels.MANAGED_BY, ResourceLabels.MANAGED_BY_VALUE)
                .withLabel(ResourceLabels.OWNER, Names.toDnsLabel(owner))
                .list().getItems();
        for (Pod pod : pods) {
            client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).withGracePeriod(0).delete();
            LOG.info("Deleted {}/{}", namespace, pod.getMetadata().getName());
        }
        return pods.size();
    }
}
