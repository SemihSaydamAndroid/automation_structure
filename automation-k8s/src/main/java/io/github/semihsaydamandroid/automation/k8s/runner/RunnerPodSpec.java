package io.github.semihsaydamandroid.automation.k8s.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.util.Names;
import io.github.semihsaydamandroid.automation.k8s.support.K8sSettings;
import io.github.semihsaydamandroid.automation.k8s.support.PodSpecs;
import io.github.semihsaydamandroid.automation.k8s.support.ResourceLabels;

/**
 * Pod that hosts a test run. It starts idle; the {@link RemoteRunner} uploads the workspace and
 * executes the command through the API server, streaming output back to the caller.
 */
public final class RunnerPodSpec {

    public static final String WORKSPACE = "/workspace";
    public static final String SECRETS_DIR = "/etc/automation/secrets";

    private RunnerPodSpec() {
    }

    public static Pod build(K8sSettings settings, ExecutionContext context, Map<String, String> extraEnv) {
        K8sSettings.Runner runner = settings.runner();
        List<EnvVar> env = new ArrayList<>();
        env.add(env("AUTOMATION_PROFILE", context.profile().id()));
        env.add(env("AUTOMATION_ENV", context.env()));
        env.add(env("AUTOMATION_RUN_ID", context.runId()));
        env.add(env("AUTOMATION_OWNER", context.owner()));
        env.add(env("AUTOMATION_K8S_NAMESPACE", settings.namespace()));
        // Browsers cannot run inside the Maven image; they become sibling pods reached by pod IP.
        env.add(env("AUTOMATION_UI_EXECUTION", "kubernetes"));
        env.add(env("MAVEN_OPTS", "-XX:MaxRAMPercentage=75"));
        extraEnv.forEach((k, v) -> env.add(env(k, v)));
        env.add(fieldRef("POD_NAME", "metadata.name"));
        env.add(fieldRef("POD_UID", "metadata.uid"));
        env.add(fieldRef("POD_NAMESPACE", "metadata.namespace"));

        List<Volume> volumes = new ArrayList<>();
        List<VolumeMount> mounts = new ArrayList<>();
        volumes.add(new VolumeBuilder().withName("workspace").withNewEmptyDir().endEmptyDir().build());
        mounts.add(new VolumeMountBuilder().withName("workspace").withMountPath(WORKSPACE).build());
        if (!runner.m2Pvc().isBlank()) {
            volumes.add(new VolumeBuilder().withName("m2").withNewPersistentVolumeClaim()
                    .withClaimName(runner.m2Pvc()).endPersistentVolumeClaim().build());
        } else {
            volumes.add(new VolumeBuilder().withName("m2").withNewEmptyDir().endEmptyDir().build());
        }
        mounts.add(new VolumeMountBuilder().withName("m2").withMountPath("/root/.m2/repository").build());
        if (!runner.secretName().isBlank()) {
            volumes.add(new VolumeBuilder().withName("secrets").withNewSecret()
                    .withSecretName(runner.secretName()).endSecret().build());
            mounts.add(new VolumeMountBuilder().withName("secrets").withMountPath(SECRETS_DIR).withReadOnly(true).build());
        }

        return new PodBuilder()
                .withNewMetadata()
                    .withGenerateName(Names.toDnsLabel("runner-" + context.owner(), 40) + "-")
                    .withNamespace(settings.namespace())
                    .withLabels(ResourceLabels.of(context, "runner"))
                .endMetadata()
                .withNewSpec()
                    .withRestartPolicy("Never")
                    .withActiveDeadlineSeconds(runner.maxLifetime().toSeconds())
                    .withTerminationGracePeriodSeconds(5L)
                    .withServiceAccountName(runner.serviceAccount().isBlank() ? null : runner.serviceAccount())
                    .withNodeSelector(settings.nodeSelector())
                    .withImagePullSecrets(PodSpecs.pullSecrets(settings.imagePullSecret()))
                    .addNewContainer()
                        .withName("runner")
                        .withImage(runner.image())
                        .withWorkingDir(WORKSPACE)
                        // Idle until the workspace is uploaded; the deadline bounds the lifetime anyway.
                        .withCommand("sh", "-c", "sleep " + runner.maxLifetime().toSeconds())
                        .withEnv(env)
                        .withResources(PodSpecs.resources(runner.cpuRequest(), runner.cpuLimit(),
                                runner.memoryRequest(), runner.memoryLimit()))
                        .withVolumeMounts(mounts)
                    .endContainer()
                    .withVolumes(volumes)
                .endSpec()
                .build();
    }

    private static EnvVar env(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }

    private static EnvVar fieldRef(String name, String path) {
        return new EnvVarBuilder().withName(name)
                .withNewValueFrom().withNewFieldRef().withFieldPath(path).endFieldRef().endValueFrom()
                .build();
    }
}
