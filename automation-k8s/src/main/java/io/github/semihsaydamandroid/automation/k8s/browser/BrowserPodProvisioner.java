package io.github.semihsaydamandroid.automation.k8s.browser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.data.TestData;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings.ConnectionMode;
import io.github.semihsaydamandroid.automation.k8s.support.PodDiagnostics;

/**
 * Creates a browser pod, waits until Selenium inside is ready and returns an endpoint reachable
 * from this JVM: the pod IP when running in the cluster, otherwise a port-forward through the API
 * server, so a laptop only needs kubectl access, not cluster network access.
 */
public final class BrowserPodProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(BrowserPodProvisioner.class);

    private final KubernetesClient client;
    private final K8sSettings settings;
    private final ExecutionContext context;
    private final Map<String, String> env;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public BrowserPodProvisioner(KubernetesClient client, K8sSettings settings, ExecutionContext context, Map<String, String> env) {
        this.client = client;
        this.settings = settings;
        this.context = context;
        this.env = env;
    }

    public BrowserPod provision(String browserName) {
        Pod spec = BrowserPodSpec.build(browserName, settings, context, env);
        Pod created = client.pods().inNamespace(settings.namespace()).resource(spec).create();
        String name = created.getMetadata().getName();
        LOG.info("Created browser pod {}/{} ({})", settings.namespace(), name, settings.browser().image(browserName));
        List<LocalPortForward> forwards = new ArrayList<>();
        try {
            Duration timeout = settings.browser().startupTimeout();
            Pod ready;
            try {
                ready = client.pods().inNamespace(settings.namespace()).withName(name)
                        .waitUntilReady(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (KubernetesClientTimeoutException e) {
                throw new IllegalStateException("Browser pod not ready within " + timeout + ": "
                        + PodDiagnostics.describe(client, settings.namespace(), name), e);
            }
            URI endpoint;
            URI liveView = null;
            if (usePodIp()) {
                String ip = ready.getStatus().getPodIP();
                endpoint = URI.create("http://" + ip + ":" + BrowserPodSpec.WEBDRIVER_PORT);
                if (settings.browser().liveView()) {
                    liveView = URI.create("http://" + ip + ":" + BrowserPodSpec.NOVNC_PORT + "/?autoconnect=1&resize=scale");
                }
            } else {
                LocalPortForward webdriver = client.pods().inNamespace(settings.namespace()).withName(name)
                        .portForward(BrowserPodSpec.WEBDRIVER_PORT);
                forwards.add(webdriver);
                endpoint = URI.create("http://127.0.0.1:" + webdriver.getLocalPort());
                if (settings.browser().liveView()) {
                    LocalPortForward vnc = client.pods().inNamespace(settings.namespace()).withName(name)
                            .portForward(BrowserPodSpec.NOVNC_PORT);
                    forwards.add(vnc);
                    liveView = URI.create("http://127.0.0.1:" + vnc.getLocalPort() + "/?autoconnect=1&resize=scale");
                }
            }
            awaitSeleniumReady(endpoint, timeout);
            if (liveView != null) {
                LOG.info("Watch the browser live: {}", liveView);
            }
            return new BrowserPod(client, settings.namespace(), name, endpoint, liveView, List.copyOf(forwards));
        } catch (RuntimeException e) {
            new BrowserPod(client, settings.namespace(), name, null, null, forwards).close();
            throw e;
        }
    }

    private boolean usePodIp() {
        return switch (settings.connection()) {
            case POD_IP -> true;
            case PORT_FORWARD -> false;
            case AUTO -> context.insideKubernetes();
        };
    }

    /** The container can be "ready" slightly before the node registers with the embedded router. */
    private void awaitSeleniumReady(URI endpoint, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/status")).timeout(Duration.ofSeconds(5)).GET().build();
        String last = "no response";
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode ready = TestData.mapper().readTree(response.body()).path("value").path("ready");
                if (ready.asBoolean(false)) {
                    return;
                }
                last = "HTTP " + response.statusCode() + " ready=" + ready;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Selenium at " + endpoint, e);
            } catch (Exception e) {
                last = e.toString();
            }
            sleep(Duration.ofMillis(500));
        }
        throw new IllegalStateException("Selenium at " + endpoint + " did not become ready: " + last);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
