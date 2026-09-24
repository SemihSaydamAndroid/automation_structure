package io.github.semihsaydamandroid.automation.ai.model;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/** Builds LangChain4j chat models for the configured provider. */
public final class AiModels {

    private AiModels() {
    }

    public static ChatModel create(AiSettings settings, String modelName) {
        return switch (settings.provider()) {
            case OLLAMA -> OllamaChatModel.builder()
                    .baseUrl(settings.baseUrl())
                    .modelName(modelName)
                    .temperature(settings.temperature())
                    .numPredict(settings.maxTokens())
                    .timeout(settings.timeout())
                    .build();
            case OPENAI_COMPATIBLE -> {
                if (settings.baseUrl().isBlank() || modelName.isBlank()) {
                    throw new IllegalStateException("ai.provider=openai-compatible requires ai.base-url and ai.model");
                }
                yield OpenAiChatModel.builder()
                        .baseUrl(settings.baseUrl())
                        .apiKey(settings.apiKey().isBlank() ? "not-needed" : settings.apiKey())
                        .modelName(modelName)
                        .temperature(settings.temperature())
                        .maxTokens(settings.maxTokens())
                        .timeout(settings.timeout())
                        .build();
            }
            case ANTHROPIC -> {
                if (settings.apiKey().isBlank()) {
                    throw new IllegalStateException("ai.provider=anthropic requires ai.api-key (e.g. ${secret:anthropic-api-key})");
                }
                AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                        .apiKey(settings.apiKey())
                        .modelName(modelName)
                        .temperature(settings.temperature())
                        .maxTokens(settings.maxTokens())
                        .timeout(settings.timeout());
                if (!settings.baseUrl().isBlank()) {
                    builder.baseUrl(settings.baseUrl());
                }
                yield builder.build();
            }
            case NONE -> throw new IllegalStateException("AI is disabled (ai.provider=none)");
        };
    }
}
