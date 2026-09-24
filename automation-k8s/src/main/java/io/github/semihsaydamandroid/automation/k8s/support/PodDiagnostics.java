package io.github.semihsaydamandroid.automation.k8s.support;

import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.client.KubernetesClient;

/** Human readable explanation of why a pod is not ready (image pull, quota, scheduling...). */
public final class PodDiagnostics {

    private PodDiagnostics() {
    }

    public static String describe(KubernetesClient client, String namespace, String name) {
        StringBuilder out = new StringBuilder("pod ").append(namespace).append('/').append(name);
        try {
            Pod pod = client.pods().inNamespace(namespace).withName(name).get();
            if (pod == null) {
                return out.append(" no longer exists").toString();
            }
            if (pod.getStatus() != null) {
                out.append(" phase=").append(pod.getStatus().getPhase());
                for (PodCondition condition : pod.getStatus().getConditions()) {
                    if (!"True".equals(condition.getStatus()) && condition.getMessage() != null) {
                        out.append("; ").append(condition.getType()).append(": ").append(condition.getMessage());
                    }
                }
                for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
                    if (status.getState() != null && status.getState().getWaiting() != null) {
                        out.append("; container ").append(status.getName()).append(" waiting: ")
                                .append(status.getState().getWaiting().getReason()).append(' ')
                                .append(nullToEmpty(status.getState().getWaiting().getMessage()));
                    }
                }
            }
            String events = client.v1().events().inNamespace(namespace)
                    .withField("involvedObject.name", name).list().getItems().stream()
                    .filter(e -> !"Normal".equals(e.getType()))
                    .map(e -> e.getReason() + ": " + e.getMessage())
                    .collect(Collectors.joining("; "));
            if (!events.isEmpty()) {
                out.append("; events: ").append(events);
            }
        } catch (RuntimeException e) {
            out.append(" (diagnostics unavailable: ").append(e.getMessage()).append(')');
        }
        return out.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
