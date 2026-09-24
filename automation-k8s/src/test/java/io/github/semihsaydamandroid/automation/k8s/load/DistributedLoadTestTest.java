package io.github.semihsaydamandroid.automation.k8s.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpSampler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.perf.LoadProfile;
import io.github.semihsaydamandroid.automation.perf.PerfTest;

class DistributedLoadTestTest {

    private static AutomationConfig config(Map<String, String> extra) {
        Map<String, String> values = new java.util.HashMap<>(Map.of(
                "k8s.namespace.ci", "qa-ci",
                "automation.profile", "ci",
                "k8s.perf.image", "registry.local/jmeter-worker:5.6.3",
                "k8s.perf.node-selector.pool", "loadgen",
                "k8s.perf.tolerations", "dedicated=loadgen:NoSchedule,spot:NoSchedule",
                "k8s.perf.collect-timeout", "5m"));
        values.putAll(extra);
        return AutomationConfig.of(values);
    }

    @Test
    void workersRunAsIndexedJobOnTheLoadGeneratorPool() {
        AutomationConfig config = config(Map.of());
        K8sSettings settings = K8sSettings.from(config);
        LoadSettings load = LoadSettings.from(config);

        Job job = LoadWorkerJobSpec.job("load-checkout-ci", "plan-cm", 40, settings, load, config.context());

        assertThat(job.getMetadata().getNamespace()).isEqualTo("qa-ci");
        assertThat(job.getSpec().getCompletionMode()).isEqualTo("Indexed");
        assertThat(job.getSpec().getParallelism()).isEqualTo(40);
        assertThat(job.getSpec().getCompletions()).isEqualTo(40);
        assertThat(job.getSpec().getBackoffLimit()).isZero();
        PodSpec pod = job.getSpec().getTemplate().getSpec();
        assertThat(pod.getNodeSelector()).containsEntry("pool", "loadgen");
        assertThat(pod.getTolerations()).hasSize(2);
        assertThat(pod.getTolerations().get(0).getValue()).isEqualTo("loadgen");
        assertThat(pod.getTolerations().get(1).getOperator()).isEqualTo("Exists");
        Container container = pod.getContainers().get(0);
        assertThat(container.getImage()).isEqualTo("registry.local/jmeter-worker:5.6.3");
        assertThat(container.getReadinessProbe().getExec().getCommand()).contains(LoadWorkerJobSpec.DONE_MARKER);
        assertThat(container.getEnv()).anySatisfy(e -> {
            assertThat(e.getName()).isEqualTo("COLLECT_SECONDS");
            assertThat(e.getValue()).isEqualTo("300");
        });
        assertThat(LoadWorkerJobSpec.WORKER_SCRIPT)
                .contains("while [ ! -f /tmp/go ]", "-Jworker.index=\"${JOB_COMPLETION_INDEX}\"", "-q /plan/plan.properties");
    }

    @Test
    void generatedPlanTargetsAreCheckedAgainstTheAllowlist(@TempDir Path dir) throws Exception {
        AutomationConfig.set(AutomationConfig.of(Map.of("perf.profile", "smoke",
                "perf.profile.smoke.threads", "1", "perf.profile.smoke.iterations", "1")));
        try {
            Path jmx = dir.resolve("plan.jmx");
            PerfTest.named("checkout")
                    .scenario(httpSampler("list", "https://shop.test.example.com/api/products"),
                            httpSampler("pay", "https://payments.example.com/pay"))
                    .saveAsJmx(jmx, LoadProfile.iterations(50, 1));

            JmxTargets.Result targets = JmxTargets.scan(Files.readString(jmx), Map.of());

            assertThat(targets.hosts()).containsExactlyInAnyOrder("shop.test.example.com", "payments.example.com");
            JmxTargets.requireAllowed(targets, List.of("*.example.com"));
            assertThatThrownBy(() -> JmxTargets.requireAllowed(targets, List.of("shop.test.example.com")))
                    .hasMessageContaining("payments.example.com");
            assertThatThrownBy(() -> JmxTargets.requireAllowed(targets, List.of()))
                    .hasMessageContaining("k8s.perf.allowed-hosts");
        } finally {
            AutomationConfig.reset();
        }
    }

    @Test
    void propertyBasedHostsAreResolvedAndUnknownVariablesRefused() {
        String jmx = """
                <stringProp name="HTTPSampler.domain">${__P(host,staging.example.com)}</stringProp>
                <stringProp name="HTTPSampler.domain">${__P(other)}</stringProp>
                """;

        JmxTargets.Result withProps = JmxTargets.scan(jmx, Map.of("other", "api.example.com"));
        JmxTargets.Result withoutProps = JmxTargets.scan(jmx, Map.of());

        assertThat(withProps.hosts()).containsExactly("staging.example.com", "api.example.com");
        assertThat(withoutProps.unresolved()).containsExactly("${__P(other)}");
        assertThatThrownBy(() -> JmxTargets.requireAllowed(withoutProps, List.of("*.example.com")))
                .hasMessageContaining("Cannot determine load targets");
    }

    @Test
    void laptopsCannotLaunchLargeDistributedLoad() {
        AutomationConfig local = config(Map.of("automation.profile", "local"));
        LoadSettings load = LoadSettings.from(local);

        assertThatThrownBy(() -> DistributedLoadTest.guard(local.context(), load, 40))
                .hasMessageContaining("k8s.perf.guard.local-max-workers=2");
        DistributedLoadTest.guard(local.context(), load, 2);
        DistributedLoadTest.guard(config(Map.of()).context(), load, 40); // ci
    }

    @Test
    void propertiesAreEscapedForJmeter() throws Exception {
        String line = "host=" + DistributedLoadTest.escape("ödeme.example.com:8443") + "\n";
        Properties properties = new Properties();
        properties.load(new java.io.ByteArrayInputStream(line.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));

        assertThat(properties.getProperty("host")).isEqualTo("ödeme.example.com:8443");
    }
}
