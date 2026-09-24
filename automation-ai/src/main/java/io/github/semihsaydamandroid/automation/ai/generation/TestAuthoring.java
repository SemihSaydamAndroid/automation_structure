package io.github.semihsaydamandroid.automation.ai.generation;

import java.util.Map;

import io.github.semihsaydamandroid.automation.ai.model.AiClient;
import io.github.semihsaydamandroid.automation.ai.model.DomCompactor;
import io.github.semihsaydamandroid.automation.ai.model.Prompts;

/**
 * Drafts tests for humans to review: Karate features from an OpenAPI document and Selenium page
 * objects from a live page. Output follows this framework's conventions (KarateBridge config,
 * BasePage/Locator with descriptions) so drafts compile and run with small edits.
 */
public final class TestAuthoring {

    private final AiClient ai;

    public TestAuthoring(AiClient ai) {
        this.ai = ai;
    }

    public static TestAuthoring fromConfig() {
        return new TestAuthoring(AiClient.shared().orElseThrow(() ->
                new IllegalStateException("Test authoring needs ai.provider (ollama, openai-compatible or anthropic)")));
    }

    /** Karate feature covering the given operations of an OpenAPI (JSON or YAML) document. */
    public String karateFeature(String openApi, String focus) {
        String answer = ai.complete(Prompts.render("karate-feature-system", Map.of()),
                Prompts.render("karate-feature", Map.of("openapi", ai.prepare(openApi), "focus", focus)));
        return stripFences(answer);
    }

    /** Java page object for the given page source. */
    public String pageObject(String className, String packageName, String url, String html) {
        String answer = ai.complete(Prompts.render("page-object-system", Map.of()),
                Prompts.render("page-object", Map.of("className", className, "packageName", packageName,
                        "url", url, "dom", ai.prepare(DomCompactor.compact(html)))));
        return stripFences(answer);
    }

    static String stripFences(String text) {
        String result = text.strip();
        if (result.startsWith("```")) {
            result = result.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```\\s*$", "");
        }
        return result + "\n";
    }
}
