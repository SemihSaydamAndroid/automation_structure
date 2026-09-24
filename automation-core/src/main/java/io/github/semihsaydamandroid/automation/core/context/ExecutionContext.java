package io.github.semihsaydamandroid.automation.core.context;

/**
 * Identity of the current run, shared by every module.
 *
 * @param env              target environment name (dev, test, preprod, ...)
 * @param profile          whether the run was started locally or by a pipeline
 * @param runId            stable id for this run; used for report grouping and Kubernetes labels
 * @param owner            who started the run: the OS user locally, {@code ci} in pipelines
 * @param insideKubernetes true when this JVM itself runs in a pod (remote runner, CI agent pod)
 */
public record ExecutionContext(
        String env,
        ExecutionProfile profile,
        String runId,
        String owner,
        boolean insideKubernetes) {

    public boolean isCi() {
        return profile == ExecutionProfile.CI;
    }

    public boolean isLocal() {
        return profile == ExecutionProfile.LOCAL;
    }
}
