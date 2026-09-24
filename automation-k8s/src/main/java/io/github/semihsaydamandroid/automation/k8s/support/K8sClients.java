package io.github.semihsaydamandroid.automation.k8s.support;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

/**
 * Creates clients from the standard sources: in-cluster service account when running in a pod,
 * otherwise {@code ~/.kube/config} (optionally a specific context via {@code k8s.context}).
 */
public final class K8sClients {

    private static volatile KubernetesClient shared;

    private K8sClients() {
    }

    public static KubernetesClient create(K8sSettings settings) {
        String context = settings.kubeContext() == null || settings.kubeContext().isBlank() ? null : settings.kubeContext();
        return new KubernetesClientBuilder().withConfig(Config.autoConfigure(context)).build();
    }

    /**
     * Process-wide client. Deliberately not closed by a shutdown hook: the driver cleanup hook
     * still needs it to delete browser pods while the JVM exits.
     */
    public static KubernetesClient shared(K8sSettings settings) {
        KubernetesClient client = shared;
        if (client == null) {
            synchronized (K8sClients.class) {
                if (shared == null) {
                    shared = create(settings);
                }
                client = shared;
            }
        }
        return client;
    }
}
