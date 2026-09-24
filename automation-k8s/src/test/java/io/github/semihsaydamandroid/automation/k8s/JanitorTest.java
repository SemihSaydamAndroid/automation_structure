package io.github.semihsaydamandroid.automation.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.github.semihsaydamandroid.automation.k8s.runner.Janitor;
import io.github.semihsaydamandroid.automation.k8s.support.ResourceLabels;

@EnableKubernetesMockClient(crud = true)
class JanitorTest {

    KubernetesClient client;

    @Test
    void deletesOnlyManagedPodsOfTheOwner() {
        create("mine-1", Map.of(ResourceLabels.MANAGED_BY, ResourceLabels.MANAGED_BY_VALUE, ResourceLabels.OWNER, "semih"));
        create("mine-2", Map.of(ResourceLabels.MANAGED_BY, ResourceLabels.MANAGED_BY_VALUE, ResourceLabels.OWNER, "semih"));
        create("colleague", Map.of(ResourceLabels.MANAGED_BY, ResourceLabels.MANAGED_BY_VALUE, ResourceLabels.OWNER, "ayse"));
        create("unmanaged", Map.of(ResourceLabels.OWNER, "semih"));

        int deleted = new Janitor(client).cleanup("qa-local", "semih");

        assertThat(deleted).isEqualTo(2);
        assertThat(client.pods().inNamespace("qa-local").list().getItems())
                .extracting(p -> p.getMetadata().getName())
                .containsExactlyInAnyOrder("colleague", "unmanaged");
    }

    private void create(String name, Map<String, String> labels) {
        client.pods().inNamespace("qa-local").resource(new PodBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec().addNewContainer().withName("c").withImage("busybox").endContainer().endSpec()
                .build()).create();
    }
}
