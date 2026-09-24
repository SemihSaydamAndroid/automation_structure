package io.github.semihsaydamandroid.automation.core.secrets;

import java.util.Map;
import java.util.Optional;

import io.github.semihsaydamandroid.automation.core.util.Names;

/**
 * Resolves {@code ${secret:db-password}} from {@code AUTOMATION_SECRET_DB_PASSWORD} or, failing
 * that, {@code DB_PASSWORD}. This is what CI systems (Jenkins credentials, GitHub secrets) inject.
 */
public final class EnvSecretProvider implements SecretProvider {

    private final Map<String, String> env;

    public EnvSecretProvider() {
        this(System.getenv());
    }

    EnvSecretProvider(Map<String, String> env) {
        this.env = env;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Optional<String> lookup(String name) {
        String normalized = Names.toEnvStyle(name);
        String prefixed = env.get("AUTOMATION_SECRET_" + normalized);
        if (prefixed != null) {
            return Optional.of(prefixed);
        }
        return Optional.ofNullable(env.get(normalized));
    }
}
