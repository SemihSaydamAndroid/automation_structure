package io.github.semihsaydamandroid.automation.k8s.browser;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;

/**
 * A running browser pod. Closing it tears down port-forwards and deletes the pod immediately.
 *
 * @param endpoint WebDriver endpoint reachable from this JVM (pod IP or local forwarded port)
 * @param liveView noVNC URL to watch the browser, when enabled
 */
public record BrowserPod(
        KubernetesClient client,
        String namespace,
        String name,
        URI endpoint,
        URI liveView,
        List<LocalPortForward> forwards) implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BrowserPod.class);

    @Override
    public void close() {
        for (LocalPortForward forward : forwards) {
            try {
                forward.close();
            } catch (IOException e) {
                LOG.debug("Closing port-forward failed: {}", e.toString());
            }
        }
        try {
            client.pods().inNamespace(namespace).withName(name).withGracePeriod(0).delete();
            LOG.info("Deleted browser pod {}/{}", namespace, name);
        } catch (RuntimeException e) {
            LOG.warn("Could not delete browser pod {}/{}: {} (activeDeadlineSeconds will reap it)",
                    namespace, name, e.toString());
        }
    }

    @Override
    public String toString() {
        return "pod/" + namespace + "/" + name;
    }
}
