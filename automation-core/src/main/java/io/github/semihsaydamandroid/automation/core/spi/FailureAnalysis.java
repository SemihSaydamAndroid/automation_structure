package io.github.semihsaydamandroid.automation.core.spi;

/**
 * Triage verdict for a failed test.
 *
 * @param category     classification used for dashboards and routing (bug ticket vs. test fix)
 * @param confidence   0..1
 * @param summary      one sentence a human reads first
 * @param rootCause    most likely cause
 * @param suggestedFix concrete next action
 * @param analyzer     which analyzer produced this (for example {@code rules} or {@code ai:ollama/llama3.1})
 */
public record FailureAnalysis(
        Category category,
        double confidence,
        String summary,
        String rootCause,
        String suggestedFix,
        String analyzer) {

    public enum Category {
        PRODUCT_BUG,
        TEST_BUG,
        LOCATOR_CHANGED,
        ENVIRONMENT,
        TEST_DATA,
        FLAKY,
        UNKNOWN
    }

    public String toMarkdown() {
        return """
                ### Failure analysis (%s)
                **Category:** %s (confidence %.0f%%)

                **Summary:** %s

                **Root cause:** %s

                **Suggested fix:** %s
                """.formatted(analyzer, category, confidence * 100, summary, rootCause, suggestedFix);
    }
}
