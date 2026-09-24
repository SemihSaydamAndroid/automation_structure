package io.github.semihsaydamandroid.automation.k8s.load;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/** Typed view of {@code k8s.perf.*}: load generator pods for distributed JMeter runs. */
public record LoadSettings(
        String image,
        String jmeterCommand,
        int workers,
        String cpuRequest,
        String cpuLimit,
        String memoryRequest,
        String memoryLimit,
        Map<String, String> nodeSelector,
        List<Toleration> tolerations,
        List<String> allowedHosts,
        int localMaxWorkers,
        boolean allowLocalLoad,
        Duration startupTimeout,
        Duration maxLifetime,
        Duration collectTimeout,
        boolean keep) {

    public static LoadSettings from(AutomationConfig config) {
        Map<String, String> nodeSelector = new LinkedHashMap<>(config.section("k8s.node-selector"));
        nodeSelector.putAll(config.section("k8s.perf.node-selector"));
        return new LoadSettings(
                config.get("k8s.perf.image", "ghcr.io/semihsaydamandroid/jmeter-worker:5.6.3"),
                config.get("k8s.perf.jmeter-command", "jmeter"),
                config.getInt("k8s.perf.workers", 2),
                config.get("k8s.perf.cpu", "1"),
                config.get("k8s.perf.cpu-limit", "2"),
                config.get("k8s.perf.memory", "1Gi"),
                config.get("k8s.perf.memory-limit", "2Gi"),
                nodeSelector,
                parseTolerations(config.getList("k8s.perf.tolerations")),
                config.getList("k8s.perf.allowed-hosts"),
                config.getInt("k8s.perf.guard.local-max-workers", 2),
                config.getBoolean("k8s.perf.guard.allow-local-load", false),
                config.getDuration("k8s.perf.startup-timeout", Duration.ofMinutes(5)),
                config.getDuration("k8s.perf.max-lifetime", Duration.ofHours(2)),
                config.getDuration("k8s.perf.collect-timeout", Duration.ofMinutes(10)),
                config.getBoolean("k8s.perf.keep", false));
    }

    /** {@code dedicated=loadgen:NoSchedule} or {@code spot:NoSchedule} (key exists). */
    static List<Toleration> parseTolerations(List<String> specs) {
        List<Toleration> result = new ArrayList<>();
        for (String spec : specs) {
            String[] keyAndEffect = spec.split(":", 2);
            String effect = keyAndEffect.length > 1 ? keyAndEffect[1] : null;
            String[] keyValue = keyAndEffect[0].split("=", 2);
            TolerationBuilder builder = new TolerationBuilder().withKey(keyValue[0]).withEffect(effect);
            if (keyValue.length > 1) {
                builder.withOperator("Equal").withValue(keyValue[1]);
            } else {
                builder.withOperator("Exists");
            }
            result.add(builder.build());
        }
        return result;
    }
}
