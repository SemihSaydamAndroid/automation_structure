package io.github.semihsaydamandroid.automation.ai.assertion;

import java.util.Map;

import io.github.semihsaydamandroid.automation.ai.model.AiClient;
import io.github.semihsaydamandroid.automation.ai.model.Prompts;
import io.github.semihsaydamandroid.automation.core.report.Reporter;

/**
 * Natural-language visual checks with a vision model (Llama 3.2 Vision, Qwen2.5-VL, Claude...):
 *
 * <pre>{@code
 * AiAssertions.assertScreenshot(screenshotPng, "The cart shows exactly 2 items and a red 'Out of stock' badge");
 * }</pre>
 *
 * Model verdicts are probabilistic: use them for checks that are hard to express with locators
 * (layout, charts, rendered PDFs, canvas), keep functional assertions deterministic.
 */
public final class AiAssertions {

    public record Verdict(boolean pass, double confidence, String reason) {
    }

    private AiAssertions() {
    }

    public static Verdict evaluate(byte[] png, String expectation) {
        AiClient ai = AiClient.shared().orElseThrow(() ->
                new IllegalStateException("Visual AI assertions need ai.provider and a vision capable ai.vision-model"));
        return ai.json(Prompts.render("visual-assertion-system", Map.of()),
                Prompts.render("visual-assertion", Map.of("expectation", expectation)), png, Verdict.class);
    }

    public static Verdict assertScreenshot(byte[] png, String expectation) {
        Verdict verdict = evaluate(png, expectation);
        Reporter.attachText("AI visual check", "Expectation: " + expectation + "\nPass: " + verdict.pass()
                + " (confidence " + verdict.confidence() + ")\nReason: " + verdict.reason());
        if (!verdict.pass()) {
            throw new AssertionError("Visual expectation not met: " + expectation + " - " + verdict.reason());
        }
        return verdict;
    }
}
