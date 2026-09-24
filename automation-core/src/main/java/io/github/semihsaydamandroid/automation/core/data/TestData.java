package io.github.semihsaydamandroid.automation.core.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import net.datafaker.Faker;

/**
 * Test data helpers usable from Java tests and from Karate
 * ({@code Java.type('io.github.semihsaydamandroid.automation.core.data.TestData')}).
 */
public final class TestData {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final ThreadLocal<Faker> FAKER = ThreadLocal.withInitial(() ->
            new Faker(Locale.forLanguageTag(AutomationConfig.get().get("data.locale", "tr"))));

    private TestData() {
    }

    /** Thread-confined Datafaker instance using {@code data.locale} (default {@code tr}). */
    public static Faker faker() {
        return FAKER.get();
    }

    /** Short unique value, safe for usernames, e-mails and Kubernetes names. */
    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static JsonNode json(String classpathResource) {
        return read(classpathResource, JsonNode.class);
    }

    public static <T> T read(String classpathResource, Class<T> type) {
        String resource = classpathResource.startsWith("classpath:")
                ? classpathResource.substring("classpath:".length())
                : classpathResource;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("Test data not found on classpath: " + resource);
            }
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot parse test data " + resource, e);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
