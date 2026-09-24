package io.github.semihsaydamandroid.automation.k8s.support;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.util.Names;

/**
 * Labels put on every resource the framework creates. They make local and pipeline resources
 * distinguishable, allow per-owner cleanup and cost reporting by run.
 */
public final class ResourceLabels {

    public static final String MANAGED_BY = "app.kubernetes.io/managed-by";
    public static final String MANAGED_BY_VALUE = "automation-structure";
    public static final String COMPONENT = "app.kubernetes.io/component";
    public static final String PROFILE = "automation/profile";
    public static final String OWNER = "automation/owner";
    public static final String RUN_ID = "automation/run-id";
    public static final String ENV = "automation/env";

    private ResourceLabels() {
    }

    public static Map<String, String> of(ExecutionContext context, String component) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(MANAGED_BY, MANAGED_BY_VALUE);
        labels.put(COMPONENT, component);
        labels.put(PROFILE, context.profile().id());
        labels.put(OWNER, Names.toDnsLabel(context.owner()));
        labels.put(RUN_ID, Names.toDnsLabel(context.runId()));
        labels.put(ENV, Names.toDnsLabel(context.env()));
        return labels;
    }
}
