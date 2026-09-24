package io.github.semihsaydamandroid.automation.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.k8s.browser.BrowserPodSpec;
import io.github.semihsaydamandroid.automation.k8s.runner.RunnerPodSpec;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.k8s.support.ResourceLabels;

class PodSpecTest {

    private static AutomationConfig config(Map<String, String> values) {
        Map<String, String> all = new java.util.HashMap<>(Map.of(
                "k8s.namespace.local", "qa-local",
                "k8s.namespace.ci", "qa-ci",
                "k8s.browser.image.chrome", "selenium/standalone-chrome:4.35.0",
                "k8s.browser.max-lifetime", "30m",
                "automation.owner", "Şeyma Öztürk",
                "automation.run-id", "run-42"));
        all.putAll(values);
        return AutomationConfig.of(all);
    }

    @Test
    void browserPodIsIsolatedPerProfileAndOwner() {
        AutomationConfig config = config(Map.of());
        K8sSettings settings = K8sSettings.from(config);

        Pod pod = BrowserPodSpec.build("chrome", settings, config.context(), Map.of());

        assertThat(pod.getMetadata().getNamespace()).isEqualTo("qa-local");
        assertThat(pod.getMetadata().getGenerateName()).startsWith("browser-chrome-seyma-ozturk");
        assertThat(pod.getMetadata().getLabels())
                .containsEntry(ResourceLabels.PROFILE, "local")
                .containsEntry(ResourceLabels.OWNER, "seyma-ozturk")
                .containsEntry(ResourceLabels.RUN_ID, "run-42")
                .containsEntry(ResourceLabels.COMPONENT, "browser");
        assertThat(pod.getSpec().getActiveDeadlineSeconds()).isEqualTo(1800L);
        assertThat(pod.getMetadata().getOwnerReferences()).isEmpty();
        Container container = pod.getSpec().getContainers().get(0);
        assertThat(container.getImage()).isEqualTo("selenium/standalone-chrome:4.35.0");
        assertThat(container.getVolumeMounts()).anyMatch(m -> m.getMountPath().equals("/dev/shm"));
        assertThat(pod.getSpec().getVolumes().get(0).getEmptyDir().getMedium()).isEqualTo("Memory");
    }

    @Test
    void ciProfileUsesCiNamespaceAndBrowserPodsAreOwnedByTheRunnerPod() {
        AutomationConfig config = config(Map.of("automation.profile", "ci"));
        K8sSettings settings = K8sSettings.from(config);

        Pod pod = BrowserPodSpec.build("chrome", settings, config.context(),
                Map.of("POD_NAME", "runner-ci-abc", "POD_UID", "uid-1", "POD_NAMESPACE", "qa-ci"));

        assertThat(pod.getMetadata().getNamespace()).isEqualTo("qa-ci");
        assertThat(pod.getMetadata().getOwnerReferences()).singleElement()
                .satisfies(ref -> assertThat(ref.getName()).isEqualTo("runner-ci-abc"));
    }

    @Test
    void explicitNamespaceWins() {
        K8sSettings settings = K8sSettings.from(config(Map.of("k8s.namespace", "team-payments-qa")));

        assertThat(settings.namespace()).isEqualTo("team-payments-qa");
    }

    @Test
    void runnerPodForwardsRunIdentityAndMountsCacheAndSecrets() {
        AutomationConfig config = config(Map.of(
                "k8s.runner.m2-pvc", "qa-m2-cache",
                "k8s.runner.secret-name", "qa-secrets",
                "k8s.runner.service-account", "qa-runner"));
        K8sSettings settings = K8sSettings.from(config);

        Pod pod = RunnerPodSpec.build(settings, config.context(), Map.of("EXTRA", "1"));

        Container container = pod.getSpec().getContainers().get(0);
        assertThat(container.getEnv()).extracting(EnvVar::getName)
                .contains("AUTOMATION_PROFILE", "AUTOMATION_RUN_ID", "AUTOMATION_OWNER",
                        "AUTOMATION_UI_EXECUTION", "POD_NAME", "POD_UID", "EXTRA");
        assertThat(pod.getSpec().getServiceAccountName()).isEqualTo("qa-runner");
        assertThat(pod.getSpec().getVolumes())
                .filteredOn(v -> v.getPersistentVolumeClaim() != null)
                .singleElement()
                .satisfies(v -> assertThat(v.getPersistentVolumeClaim().getClaimName()).isEqualTo("qa-m2-cache"));
        assertThat(container.getVolumeMounts()).anySatisfy(m ->
                assertThat(m.getMountPath()).isEqualTo(RunnerPodSpec.SECRETS_DIR));
    }
}
