package io.github.semihsaydamandroid.automation.core.secrets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import io.github.semihsaydamandroid.automation.core.util.ServiceLoaders;

/** Chain of {@link SecretProvider}s: built-ins plus anything registered via ServiceLoader. */
public final class Secrets {

    private final List<SecretProvider> providers;

    public Secrets(List<SecretProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(SecretProvider::order))
                .toList();
    }

    public static Secrets defaults() {
        List<SecretProvider> all = new ArrayList<>();
        all.add(new EnvSecretProvider());
        all.add(new FileSecretProvider());
        all.addAll(ServiceLoaders.load(SecretProvider.class));
        return new Secrets(all);
    }

    public Optional<String> find(String name) {
        for (SecretProvider provider : providers) {
            Optional<String> value = provider.lookup(name);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /** Masks a secret for logs: keeps the first two characters only. */
    public static String mask(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        return secret.length() <= 4 ? "****" : secret.substring(0, 2) + "****";
    }
}
