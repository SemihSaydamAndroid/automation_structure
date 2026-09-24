package io.github.semihsaydamandroid.automation.k8s.load;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.util.Names;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.k8s.support.PodSpecs;
import io.github.semihsaydamandroid.automation.k8s.support.ResourceLabels;

/**
 * Indexed Job of JMeter load generators. Each pod runs the same plan independently (no RMI, no
 * controller bottleneck), waits for a start signal so all workers begin together, writes its own
 * JTL and stays alive briefly so the controller can collect the results.
 */
public final class LoadWorkerJobSpec {

    public static final String PLAN_DIR = "/plan";
    public static final String RESULTS_DIR = "/results";
    public static final String START_SIGNAL = "/tmp/go";
    public static final String DONE_MARKER = RESULTS_DIR + "/done";
    public static final String COMPONENT = "load-worker";

    /** Static script; everything user-provided arrives through files or env, never interpolated. */
    static final String WORKER_SCRIPT = """
            set -u
            echo "[worker ${JOB_COMPLETION_INDEX}] ready, waiting for start signal"
            while [ ! -f %s ]; do sleep 0.2; done
            echo "[worker ${JOB_COMPLETION_INDEX}] starting load"
            "$JMETER_CMD" -n -t %s/plan.jmx -q %s/plan.properties \\
              -l %s/results.jtl -j %s/jmeter.log \\
              -Jworker.index="${JOB_COMPLETION_INDEX}" -Jworker.count="${WORKER_COUNT}" -Jdata.dir=%s \\
              -Jjmeter.save.saveservice.output_format=csv -Jjmeter.save.saveservice.print_field_names=true
            code=$?
            gzip -c %s/results.jtl > %s/results.jtl.gz 2>/dev/null || true
            echo "$code" > %s/exit-code
            touch %s
            echo "[worker ${JOB_COMPLETION_INDEX}] finished with exit code $code"
            sleep "${COLLECT_SECONDS}"
            """.formatted(START_SIGNAL, PLAN_DIR, PLAN_DIR, RESULTS_DIR, RESULTS_DIR, PLAN_DIR,
            RESULTS_DIR, RESULTS_DIR, RESULTS_DIR, DONE_MARKER);

    private LoadWorkerJobSpec() {
    }

    public static String baseName(String testName, ExecutionContext context) {
        return Names.toDnsLabel("load-" + testName + "-" + context.owner(), 40);
    }

    public static ConfigMap planConfigMap(String name, K8sSettings settings, ExecutionContext context,
                                          Map<String, String> files) {
        return new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(settings.namespace())
                    .withLabels(ResourceLabels.of(context, COMPONENT))
                .endMetadata()
                .withData(files)
                .build();
    }

    public static Job job(String generateName, String configMapName, int workers, K8sSettings settings,
                          LoadSettings load, ExecutionContext context) {
        Map<String, String> labels = ResourceLabels.of(context, COMPONENT);
        return new JobBuilder()
                .withNewMetadata()
                    .withGenerateName(generateName + "-")
                    .withNamespace(settings.namespace())
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withCompletionMode("Indexed")
                    .withCompletions(workers)
                    .withParallelism(workers)
                    // A restarted worker would silently repeat its load; fail instead.
                    .withBackoffLimit(0)
                    .withActiveDeadlineSeconds(load.maxLifetime().toSeconds())
                    .withTtlSecondsAfterFinished(3600)
                    .withNewTemplate()
                        .withNewMetadata().withLabels(labels).endMetadata()
                        .withNewSpec()
                            .withRestartPolicy("Never")
                            .withAutomountServiceAccountToken(false)
                            .withTerminationGracePeriodSeconds(5L)
                            .withNodeSelector(load.nodeSelector())
                            .withTolerations(load.tolerations())
                            .withImagePullSecrets(PodSpecs.pullSecrets(settings.imagePullSecret()))
                            .addNewContainer()
                                .withName("jmeter")
                                .withImage(load.image())
                                .withCommand("sh", "-c", WORKER_SCRIPT)
                                .addNewEnv().withName("JMETER_CMD").withValue(load.jmeterCommand()).endEnv()
                                .addNewEnv().withName("WORKER_COUNT").withValue(String.valueOf(workers)).endEnv()
                                .addNewEnv().withName("COLLECT_SECONDS").withValue(String.valueOf(load.collectTimeout().toSeconds())).endEnv()
                                .addNewEnv().withName("JVM_ARGS").withValue("-XX:MaxRAMPercentage=75").endEnv()
                                .withResources(PodSpecs.resources(load.cpuRequest(), load.cpuLimit(),
                                        load.memoryRequest(), load.memoryLimit()))
                                // Ready means "finished": the controller counts ready workers.
                                .withNewReadinessProbe()
                                    .withNewExec().withCommand("test", "-f", DONE_MARKER).endExec()
                                    .withPeriodSeconds(3)
                                .endReadinessProbe()
                                .addNewVolumeMount().withName("plan").withMountPath(PLAN_DIR).withReadOnly(true).endVolumeMount()
                                .addNewVolumeMount().withName("results").withMountPath(RESULTS_DIR).endVolumeMount()
                            .endContainer()
                            .addNewVolume().withName("plan").withNewConfigMap().withName(configMapName).endConfigMap().endVolume()
                            .addNewVolume().withName("results").withNewEmptyDir().endEmptyDir().endVolume()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }
}
