package io.github.semihsaydamandroid.automation.core.secrets;

import java.util.Optional;

/**
 * SPI for secret lookups referenced from configuration as {@code ${secret:name}}.
 * <p>
 * Built-in providers read environment variables and Kubernetes-style secret volumes. Register
 * additional ones (Vault, AWS Secrets Manager, ...) through
 * {@code META-INF/services/io.github.semihsaydamandroid.automation.core.secrets.SecretProvider}.
 */
public interface SecretProvider {

    /** Lower values are consulted first. */
    default int order() {
        return 100;
    }

    Optional<String> lookup(String name);
}
