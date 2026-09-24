package io.github.semihsaydamandroid.automation.k8s.support;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;

/** Small helpers shared by browser and runner pod specs. */
public final class PodSpecs {

    private PodSpecs() {
    }

    public static ResourceRequirements resources(String cpuRequest, String cpuLimit, String memRequest, String memLimit) {
        return new ResourceRequirementsBuilder()
                .withRequests(Map.of("cpu", new Quantity(cpuRequest), "memory", new Quantity(memRequest)))
                .withLimits(Map.of("cpu", new Quantity(cpuLimit), "memory", new Quantity(memLimit)))
                .build();
    }

    public static List<LocalObjectReference> pullSecrets(String name) {
        return name == null || name.isBlank() ? List.of() : List.of(new LocalObjectReference(name));
    }

    /**
     * When this JVM runs in a pod of the same namespace (remote runner, CI agent pod), browser pods
     * are owned by it: Kubernetes garbage-collects them if the runner disappears. The runner pod
     * exposes its identity through the downward API as {@code POD_NAME}, {@code POD_UID} and
     * {@code POD_NAMESPACE}.
     */
    public static List<OwnerReference> ownerReferences(String namespace, Map<String, String> env) {
        String name = env.get("POD_NAME");
        String uid = env.get("POD_UID");
        String podNamespace = env.get("POD_NAMESPACE");
        if (name == null || uid == null || !namespace.equals(podNamespace)) {
            return List.of();
        }
        return List.of(new OwnerReferenceBuilder()
                .withApiVersion("v1").withKind("Pod").withName(name).withUid(uid)
                .withBlockOwnerDeletion(false).withController(false)
                .build());
    }
}
