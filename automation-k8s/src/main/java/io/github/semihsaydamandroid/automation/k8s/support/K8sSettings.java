package io.github.semihsaydamandroid.automation.k8s.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;

/** Typed view of the {@code k8s.*} configuration. */
public record K8sSettings(
        String kubeContext,
        String namespace,
        ConnectionMode connection,
        String imagePullSecret,
        Map<String, String> nodeSelector,
        Browser browser,
        Runner runner) {

    /** How the test JVM reaches a browser pod. */
    public enum ConnectionMode {
        /** Pod IP when this JVM runs inside the cluster, otherwise port-forward. */
        AUTO,
        /** Always tunnel through the API server (works from laptops without cluster network access). */
        PORT_FORWARD,
        /** Always use the pod IP (in-cluster runs or flat networks). */
        POD_IP
    }

    public record Browser(
            Map<String, String> images,
            String cpuRequest,
            String cpuLimit,
            String memoryRequest,
            String memoryLimit,
            String shmSize,
            Duration startupTimeout,
            Duration maxLifetime,
            boolean liveView) {

        public String image(String browserName) {
            String image = images.get(browserName.toLowerCase());
            if (image == null) {
                throw new IllegalArgumentException("No image configured for browser '" + browserName
                        + "'. Set k8s.browser.image." + browserName.toLowerCase());
            }
            return image;
        }
    }

    public record Runner(
            String image,
            String cpuRequest,
            String cpuLimit,
            String memoryRequest,
            String memoryLimit,
            String serviceAccount,
            String m2Pvc,
            String secretName,
            String command,
            List<String> results,
            List<String> excludes,
            Duration startupTimeout,
            Duration maxLifetime,
            boolean keepPod) {
    }

    public static K8sSettings from(AutomationConfig config) {
        ExecutionContext context = config.context();
        String namespace = config.find("k8s.namespace").filter(s -> !s.isBlank())
                .orElseGet(() -> config.get("k8s.namespace." + context.profile().id(), "qa-" + context.profile().id()));
        return new K8sSettings(
                config.get("k8s.context", ""),
                namespace,
                config.getEnum("k8s.connection", ConnectionMode.class, ConnectionMode.AUTO),
                config.get("k8s.image-pull-secret", ""),
                config.section("k8s.node-selector"),
                new Browser(
                        config.section("k8s.browser.image"),
                        config.get("k8s.browser.cpu", "500m"),
                        config.get("k8s.browser.cpu-limit", "2"),
                        config.get("k8s.browser.memory", "1Gi"),
                        config.get("k8s.browser.memory-limit", "2Gi"),
                        config.get("k8s.browser.shm-size", "2Gi"),
                        config.getDuration("k8s.browser.startup-timeout", Duration.ofMinutes(3)),
                        config.getDuration("k8s.browser.max-lifetime", Duration.ofHours(1)),
                        config.getBoolean("k8s.browser.live-view", false)),
                new Runner(
                        config.get("k8s.runner.image", "maven:3.9.11-eclipse-temurin-21"),
                        config.get("k8s.runner.cpu", "1"),
                        config.get("k8s.runner.cpu-limit", "4"),
                        config.get("k8s.runner.memory", "2Gi"),
                        config.get("k8s.runner.memory-limit", "4Gi"),
                        config.get("k8s.runner.service-account", "qa-runner"),
                        config.get("k8s.runner.m2-pvc", ""),
                        config.get("k8s.runner.secret-name", ""),
                        config.get("k8s.runner.command", "mvn -B test"),
                        config.getList("k8s.runner.results"),
                        config.getList("k8s.runner.excludes"),
                        config.getDuration("k8s.runner.startup-timeout", Duration.ofMinutes(5)),
                        config.getDuration("k8s.runner.max-lifetime", Duration.ofHours(2)),
                        config.getBoolean("k8s.runner.keep-pod", false)));
    }
}
