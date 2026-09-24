package io.github.semihsaydamandroid.automation.ai.model;

import java.time.Duration;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/** Typed view of {@code ai.*}. The API key should be a {@code ${secret:...}} reference. */
public record AiSettings(
        AiProvider provider,
        String model,
        String visionModel,
        String baseUrl,
        String apiKey,
        double temperature,
        int maxTokens,
        Duration timeout,
        boolean redact,
        int maxContextChars) {

    public static AiSettings from(AutomationConfig config) {
        AiProvider provider = config.getEnum("ai.provider", AiProvider.class, AiProvider.NONE);
        String model = config.get("ai.model", "");
        if (model.isBlank()) {
            model = switch (provider) {
                case OLLAMA -> "llama3.1:8b";
                case ANTHROPIC -> "claude-sonnet-5";
                default -> "";
            };
        }
        String baseUrl = config.get("ai.base-url", "");
        if (baseUrl.isBlank() && provider == AiProvider.OLLAMA) {
            baseUrl = "http://localhost:11434";
        }
        String vision = config.get("ai.vision-model", "");
        return new AiSettings(provider, model, vision.isBlank() ? model : vision, baseUrl,
                config.get("ai.api-key", ""),
                config.getDouble("ai.temperature", 0.0),
                config.getInt("ai.max-tokens", 1024),
                config.getDuration("ai.timeout", Duration.ofSeconds(60)),
                config.getBoolean("ai.redact", true),
                config.getInt("ai.max-context-chars", 60_000));
    }

    public boolean enabled() {
        return provider != AiProvider.NONE;
    }

    public String describe() {
        return provider.name().toLowerCase() + "/" + model;
    }
}
