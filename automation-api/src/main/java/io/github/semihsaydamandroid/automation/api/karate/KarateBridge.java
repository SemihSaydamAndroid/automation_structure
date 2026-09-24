package io.github.semihsaydamandroid.automation.api.karate;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.data.TestData;

/**
 * Exposes the shared configuration to Karate, so API tests use the same environments, secrets
 * and run identity as UI and performance tests. In {@code karate-config.js}:
 *
 * <pre>{@code
 * function fn() {
 *   var Automation = Java.type('io.github.semihsaydamandroid.automation.api.karate.KarateBridge');
 *   var config = Automation.config();          // api.* keys in camelCase + env, profile, runId
 *   config.token = Automation.secret('api-token');
 *   return config;
 * }
 * }</pre>
 */
public final class KarateBridge {

    private KarateBridge() {
    }

    /** {@code api.base-url} becomes {@code baseUrl}; nested keys become {@code a.b-c} to {@code aBC}. */
    public static Map<String, Object> config() {
        AutomationConfig config = AutomationConfig.get();
        ExecutionContext context = config.context();
        Map<String, Object> result = new LinkedHashMap<>();
        config.section("api").forEach((key, value) -> result.put(camelCase(key), value));
        result.put("env", context.env());
        result.put("profile", context.profile().id());
        result.put("runId", context.runId());
        result.put("owner", context.owner());
        return result;
    }

    public static String get(String key) {
        return AutomationConfig.get().get(key);
    }

    public static String get(String key, String defaultValue) {
        return AutomationConfig.get().get(key, defaultValue);
    }

    /** Resolves a secret through the configured providers (env, mounted Kubernetes secret, vault...). */
    public static String secret(String name) {
        return AutomationConfig.get().with(Map.of("__secret", "${secret:" + name + "}")).get("__secret");
    }

    public static String unique(String prefix) {
        return TestData.unique(prefix);
    }

    static String camelCase(String key) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : key.toCharArray()) {
            if (c == '.' || c == '-' || c == '_') {
                upper = out.length() > 0;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }
}
