package io.github.semihsaydamandroid.automation.k8s.browser;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.k8s.support.K8sClients;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.ui.driver.DriverProvider;
import io.github.semihsaydamandroid.automation.ui.driver.DriverSession;
import io.github.semihsaydamandroid.automation.ui.driver.ExecutionTarget;
import io.github.semihsaydamandroid.automation.ui.driver.RemoteDriverProvider;
import io.github.semihsaydamandroid.automation.ui.driver.UiSettings;

/**
 * {@code ui.execution=kubernetes}: every browser session gets its own short-lived pod in the
 * namespace of the current profile ({@code qa-local} for developer machines, {@code qa-ci} for
 * pipelines). Works the same from a laptop (port-forward) and from inside the cluster (pod IP).
 */
public final class KubernetesDriverProvider implements DriverProvider {

    @Override
    public ExecutionTarget target() {
        return ExecutionTarget.KUBERNETES;
    }

    @Override
    public DriverSession create(UiSettings settings, Capabilities capabilities) {
        AutomationConfig config = AutomationConfig.get();
        K8sSettings k8s = K8sSettings.from(config);
        BrowserPodProvisioner provisioner =
                new BrowserPodProvisioner(K8sClients.shared(k8s), k8s, config.context(), System.getenv());
        BrowserPod pod = provisioner.provision(settings.browser().name().toLowerCase());
        try {
            WebDriver driver = RemoteDriverProvider.connect(pod.endpoint(), settings, capabilities);
            return DriverSession.of(driver, settings.browser().name().toLowerCase() + "@" + pod, pod);
        } catch (RuntimeException e) {
            pod.close();
            throw e;
        }
    }
}
