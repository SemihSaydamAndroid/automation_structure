package io.github.semihsaydamandroid.automation.ai.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.ai.model.AiClient;
import io.github.semihsaydamandroid.automation.ai.model.DomCompactor;
import io.github.semihsaydamandroid.automation.ai.model.Prompts;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.spi.FailureAnalysis;
import io.github.semihsaydamandroid.automation.core.spi.FailureAnalyzer;
import io.github.semihsaydamandroid.automation.core.spi.FailureContext;
import io.github.semihsaydamandroid.automation.core.spi.RuleBasedFailureAnalyzer;

/**
 * LLM triage of failed tests. Gets the error, stack trace, failed step, HTTP log, compacted DOM
 * and (for vision models) the screenshot, plus the rule-based verdict as a hint, and returns a
 * category with root cause and fix. Falls back to the rule-based analyzer when the model is
 * unavailable, so a model outage never breaks reporting.
 */
public final class AiFailureAnalyzer implements FailureAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(AiFailureAnalyzer.class);

    record Verdict(String category, double confidence, String summary, String rootCause, String suggestedFix) {
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Optional<FailureAnalysis> analyze(FailureContext context) {
        if (!AutomationConfig.get().getBoolean("ai.failure-analysis.enabled", true)) {
            return Optional.empty();
        }
        Optional<AiClient> client = AiClient.shared();
        if (client.isEmpty()) {
            return Optional.empty();
        }
        AiClient ai = client.get();
        try {
            Optional<FailureAnalysis> rules = new RuleBasedFailureAnalyzer().analyze(context);
            String hint = rules.map(a -> a.category() + " (" + a.rootCause() + ")").orElse("none");
            Map<String, String> values = new LinkedHashMap<>();
            values.put("testName", context.testName());
            values.put("layer", context.layer());
            values.put("error", ai.prepare(context.errorSummary(40)));
            values.put("artifacts", ai.prepare(artifacts(context)));
            values.put("ruleHint", hint);
            boolean withImage = context.screenshot() != null && AutomationConfig.get().getBoolean("ai.failure-analysis.screenshot", false);
            Verdict verdict = ai.json(Prompts.render("failure-analysis-system", Map.of()),
                    Prompts.render("failure-analysis", values), withImage ? context.screenshot() : null, Verdict.class);
            FailureAnalysis.Category category = category(verdict.category());
            String summary = verdict.summary();
            // Surface disagreement with a confident rule match instead of hiding it.
            if (rules.isPresent() && rules.get().confidence() >= 0.8 && rules.get().category() != category) {
                summary = summary + " (note: rule-based analysis suggests " + rules.get().category() + ")";
            }
            return Optional.of(new FailureAnalysis(category,
                    Math.max(0, Math.min(1, verdict.confidence())),
                    summary, verdict.rootCause(), verdict.suggestedFix(), "ai:" + ai.settings().describe()));
        } catch (RuntimeException e) {
            LOG.warn("AI failure analysis unavailable ({}); falling back to rules", e.toString());
            return Optional.empty();
        }
    }

    /** Small models paraphrase ("Environment issue", "flaky test"); map by keyword. */
    public static FailureAnalysis.Category category(String answer) {
        if (answer == null) {
            return FailureAnalysis.Category.UNKNOWN;
        }
        String value = answer.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z]+", "_");
        for (FailureAnalysis.Category category : FailureAnalysis.Category.values()) {
            if (value.equals(category.name())) {
                return category;
            }
        }
        if (value.contains("LOCATOR") || value.contains("SELECTOR") || value.contains("ELEMENT")) {
            return FailureAnalysis.Category.LOCATOR_CHANGED;
        }
        if (value.contains("ENV") || value.contains("INFRA") || value.contains("NETWORK") || value.contains("UNAVAILABLE")) {
            return FailureAnalysis.Category.ENVIRONMENT;
        }
        if (value.contains("FLAK") || value.contains("INTERMITTENT")) {
            return FailureAnalysis.Category.FLAKY;
        }
        if (value.contains("DATA")) {
            return FailureAnalysis.Category.TEST_DATA;
        }
        if (value.contains("PRODUCT") || value.contains("APPLICATION") || value.contains("REGRESSION")) {
            return FailureAnalysis.Category.PRODUCT_BUG;
        }
        if (value.contains("TEST")) {
            return FailureAnalysis.Category.TEST_BUG;
        }
        return FailureAnalysis.Category.UNKNOWN;
    }

    private static String artifacts(FailureContext context) {
        StringBuilder out = new StringBuilder();
        context.artifacts().forEach((name, value) -> {
            String content = "pageSource".equals(name) ? DomCompactor.compact(value) : value;
            out.append("### ").append(name).append('\n').append(content).append("\n\n");
        });
        return out.toString();
    }
}
