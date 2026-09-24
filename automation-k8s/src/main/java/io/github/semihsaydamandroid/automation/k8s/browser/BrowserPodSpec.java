package io.github.semihsaydamandroid.automation.k8s.browser;

import java.util.Map;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.util.Names;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.k8s.support.PodSpecs;
import io.github.semihsaydamandroid.automation.k8s.support.ResourceLabels;

/** Builds the pod for one Selenium standalone browser (one session per pod). */
public final class BrowserPodSpec {

    public static final int WEBDRIVER_PORT = 4444;
    public static final int NOVNC_PORT = 7900;

    private BrowserPodSpec() {
    }

    public static Pod build(String browserName, K8sSettings settings, ExecutionContext context, Map<String, String> env) {
        K8sSettings.Browser browser = settings.browser();
        String browserId = Names.toDnsLabel(browserName, 10);
        Map<String, String> labels = ResourceLabels.of(context, "browser");
        labels.put("automation/browser", browserId);

        return new PodBuilder()
                .withNewMetadata()
                    .withGenerateName(Names.toDnsLabel("browser-" + browserId + "-" + context.owner(), 40) + "-")
                    .withNamespace(settings.namespace())
                    .withLabels(labels)
                    .withOwnerReferences(PodSpecs.ownerReferences(settings.namespace(), env))
                .endMetadata()
                .withNewSpec()
                    .withRestartPolicy("Never")
                    // Hard stop for orphaned browsers (crashed JVM, lost laptop connection).
                    .withActiveDeadlineSeconds(browser.maxLifetime().toSeconds())
                    .withTerminationGracePeriodSeconds(5L)
                    .withAutomountServiceAccountToken(false)
                    .withNodeSelector(settings.nodeSelector())
                    .withImagePullSecrets(PodSpecs.pullSecrets(settings.imagePullSecret()))
                    .addNewContainer()
                        .withName("browser")
                        .withImage(browser.image(browserName))
                        .addNewPort().withName("webdriver").withContainerPort(WEBDRIVER_PORT).endPort()
                        .addNewPort().withName("novnc").withContainerPort(NOVNC_PORT).endPort()
                        .addNewEnv().withName("SE_NODE_MAX_SESSIONS").withValue("1").endEnv()
                        .addNewEnv().withName("SE_NODE_SESSION_TIMEOUT").withValue("300").endEnv()
                        .addNewEnv().withName("SE_START_VNC").withValue(String.valueOf(browser.liveView())).endEnv()
                        .addNewEnv().withName("SE_VNC_NO_PASSWORD").withValue(String.valueOf(context.isLocal())).endEnv()
                        .withResources(PodSpecs.resources(browser.cpuRequest(), browser.cpuLimit(),
                                browser.memoryRequest(), browser.memoryLimit()))
                        .withNewReadinessProbe()
                            .withNewHttpGet().withPath("/readyz").withPort(new IntOrString(WEBDRIVER_PORT)).endHttpGet()
                            .withPeriodSeconds(2)
                            .withFailureThreshold(3)
                        .endReadinessProbe()
                        .addNewVolumeMount().withName("dshm").withMountPath("/dev/shm").endVolumeMount()
                    .endContainer()
                    .addNewVolume()
                        .withName("dshm")
                        .withNewEmptyDir().withMedium("Memory").withSizeLimit(new Quantity(browser.shmSize())).endEmptyDir()
                    .endVolume()
                .endSpec()
                .build();
    }
}
