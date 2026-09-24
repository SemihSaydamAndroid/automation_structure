package io.github.semihsaydamandroid.automation.ai.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Prompt templates live in {@code automation/ai/prompts/<name>.md} with {@code {{placeholders}}}.
 * A client project overrides any prompt by putting a file with the same path in its test
 * resources, which come first on the classpath.
 */
public final class Prompts {

    private Prompts() {
    }

    public static String render(String name, Map<String, String> values) {
        String template = load(name);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return template;
    }

    static String load(String name) {
        String path = "automation/ai/prompts/" + name + ".md";
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = (loader != null ? loader : Prompts.class.getClassLoader()).getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Prompt not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
