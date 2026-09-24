package io.github.semihsaydamandroid.automation.ai.generation;

import java.util.List;
import java.util.Map;

import io.github.semihsaydamandroid.automation.ai.model.AiClient;
import io.github.semihsaydamandroid.automation.ai.model.Prompts;

/**
 * Realistic, domain-aware test data from a description. Usable from Karate:
 *
 * <pre>
 * * def AiData = Java.type('io.github.semihsaydamandroid.automation.ai.generation.AiTestData')
 * * def customers = AiData.generate('Turkish retail customers with name, city and a valid-looking IBAN', 5)
 * </pre>
 *
 * Prefer Datafaker ({@code TestData.faker()}) for bulk data; use this for data that needs domain
 * knowledge or edge cases (boundary values, tricky unicode, invalid-but-plausible inputs).
 */
public final class AiTestData {

    private AiTestData() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> generate(String description, int count) {
        AiClient ai = AiClient.shared().orElseThrow(() -> new IllegalStateException("AI test data needs ai.provider"));
        return ai.json(Prompts.render("test-data-system", Map.of()),
                Prompts.render("test-data", Map.of("description", description, "count", String.valueOf(count))),
                null, List.class);
    }
}
