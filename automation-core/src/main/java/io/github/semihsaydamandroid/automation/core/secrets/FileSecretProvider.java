package io.github.semihsaydamandroid.automation.core.secrets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads secrets from files, one file per secret. This matches how Kubernetes mounts a
 * {@code Secret} as a volume ({@code /etc/automation/secrets/db-password}).
 * <p>
 * The directory is taken from {@code -Dautomation.secrets.dir} or {@code AUTOMATION_SECRETS_DIR}.
 */
public final class FileSecretProvider implements SecretProvider {

    static final String DEFAULT_DIR = "/etc/automation/secrets";

    private final Path directory;

    public FileSecretProvider() {
        this(Path.of(firstNonBlank(
                System.getProperty("automation.secrets.dir"),
                System.getenv("AUTOMATION_SECRETS_DIR"),
                DEFAULT_DIR)));
    }

    public FileSecretProvider(Path directory) {
        this.directory = directory;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Optional<String> lookup(String name) {
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read secret file " + file, e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
