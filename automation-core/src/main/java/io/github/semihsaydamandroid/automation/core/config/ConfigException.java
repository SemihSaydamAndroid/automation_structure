package io.github.semihsaydamandroid.automation.core.config;

/** Thrown when a configuration value is missing or cannot be converted. */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
