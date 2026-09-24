package io.github.semihsaydamandroid.automation.ai.model;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/**
 * Minimal, provider-agnostic LLM access for the framework's AI features: text or text+image in,
 * text or JSON (mapped to a record) out. Redaction and context limits are applied here.
 */
public final class AiClient {

    private static final Logger LOG = LoggerFactory.getLogger(AiClient.class);
    /** Lenient on purpose: small local models get casing and extra fields wrong. */
    private static final ObjectMapper JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .build();

    private static volatile Optional<AiClient> shared;

    private final AiSettings settings;
    private final ChatModel textModel;
    private final ChatModel jsonModel;
    private final ChatModel visionModel;

    /**
     * @param textModel   free-form answers (generated features, page objects)
     * @param jsonModel   structured answers, JSON-constrained where supported
     * @param visionModel structured answers about screenshots
     */
    public AiClient(AiSettings settings, ChatModel textModel, ChatModel jsonModel, ChatModel visionModel) {
        this.settings = settings;
        this.textModel = textModel;
        this.jsonModel = jsonModel;
        this.visionModel = visionModel;
    }

    /** The configured client, or empty when {@code ai.provider=none}. */
    public static Optional<AiClient> shared() {
        Optional<AiClient> client = shared;
        if (client == null) {
            synchronized (AiClient.class) {
                if (shared == null) {
                    AiSettings settings = AiSettings.from(AutomationConfig.get());
                    shared = settings.enabled()
                            ? Optional.of(new AiClient(settings,
                                    AiModels.create(settings, settings.model(), false),
                                    AiModels.create(settings, settings.model(), true),
                                    AiModels.create(settings, settings.visionModel(), true)))
                            : Optional.empty();
                    shared.ifPresent(c -> LOG.info("AI features enabled with {}", settings.describe()));
                }
                client = shared;
            }
        }
        return client;
    }

    /** Replaces the shared client (tests, custom models); {@code null} forces re-reading configuration. */
    public static synchronized void use(AiClient client) {
        shared = client == null ? null : Optional.of(client);
    }

    public AiSettings settings() {
        return settings;
    }

    public String complete(String system, String user) {
        return chat(textModel, system, user, null);
    }

    /** Asks for JSON and maps it; retries once with a stricter reminder when the answer is not valid JSON. */
    public <T> T json(String system, String user, byte[] png, Class<T> type) {
        ChatModel model = png == null ? jsonModel : visionModel;
        String answer = chat(model, system, user, png);
        try {
            return JSON.readValue(extractJson(answer), type);
        } catch (JsonProcessingException first) {
            LOG.debug("Model returned invalid JSON, retrying: {}", first.getOriginalMessage());
            String retry = chat(model, system, user + "\n\nYour previous answer was not valid JSON. "
                    + "Respond with a single JSON value only, no prose, no code fences.", png);
            try {
                return JSON.readValue(extractJson(retry), type);
            } catch (JsonProcessingException second) {
                throw new IllegalStateException("Model did not return valid JSON for " + type.getSimpleName() + ": " + retry, second);
            }
        }
    }

    public String prepare(String text) {
        if (text == null) {
            return "";
        }
        String limited = text.length() > settings.maxContextChars() ? text.substring(0, settings.maxContextChars()) + "\n...[truncated]" : text;
        return settings.redact() ? Redactor.redact(limited) : limited;
    }

    private String chat(ChatModel model, String system, String user, byte[] png) {
        List<ChatMessage> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            messages.add(SystemMessage.from(system));
        }
        if (png == null) {
            messages.add(UserMessage.from(user));
        } else {
            List<Content> contents = List.of(TextContent.from(user),
                    ImageContent.from(Base64.getEncoder().encodeToString(png), "image/png"));
            messages.add(UserMessage.from(contents));
        }
        long start = System.nanoTime();
        String text = model.chat(messages).aiMessage().text();
        LOG.debug("AI answer from {} in {} ms", settings.describe(), (System.nanoTime() - start) / 1_000_000);
        return text == null ? "" : text;
    }

    /** Strips code fences and prose around the first JSON object or array. */
    static String extractJson(String answer) {
        String text = answer.strip();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```\\s*$", "");
        }
        int object = text.indexOf('{');
        int array = text.indexOf('[');
        int start = object < 0 ? array : array < 0 ? object : Math.min(object, array);
        if (start < 0) {
            return text;
        }
        char open = text.charAt(start);
        char close = open == '{' ? '}' : ']';
        int end = text.lastIndexOf(close);
        return end > start ? text.substring(start, end + 1) : text.substring(start);
    }
}
