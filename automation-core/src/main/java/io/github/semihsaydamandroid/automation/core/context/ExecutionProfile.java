package io.github.semihsaydamandroid.automation.core.context;

import java.util.Locale;
import java.util.Map;

/**
 * Where the run was started from. The profile selects {@code automation-<profile>.properties}
 * and separates resources on shared infrastructure (for example the Kubernetes namespace and
 * pod labels), so a developer's local run never collides with a pipeline run.
 */
public enum ExecutionProfile {
    LOCAL,
    CI;

    private static final String[] CI_MARKERS = {
        "JENKINS_URL", "GITHUB_ACTIONS", "GITLAB_CI", "TF_BUILD", "BUILDKITE", "TEAMCITY_VERSION",
        "BITBUCKET_BUILD_NUMBER", "CIRCLECI", "TEKTON_PIPELINE_RUN", "TESTKUBE_EXECUTION_ID"
    };

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ExecutionProfile parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    /** Detects CI servers from the environment variables they conventionally export. */
    public static ExecutionProfile detect(Map<String, String> env) {
        if ("true".equalsIgnoreCase(env.get("CI"))) {
            return CI;
        }
        for (String marker : CI_MARKERS) {
            String value = env.get(marker);
            if (value != null && !value.isBlank()) {
                return CI;
            }
        }
        return LOCAL;
    }
}
