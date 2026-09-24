package io.github.semihsaydamandroid.automation.ai.healing;

import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.ai.model.AiClient;
import io.github.semihsaydamandroid.automation.ai.model.DomCompactor;
import io.github.semihsaydamandroid.automation.ai.model.Prompts;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.ui.healing.HealingRequest;
import io.github.semihsaydamandroid.automation.ui.healing.LocatorHealer;

/**
 * Asks the model for CSS selectors matching the element's description in the current DOM. Runs
 * after the heuristic healer; the framework verifies every candidate matches exactly one visible
 * element before using it, and records the heal for a human to fix the locator.
 */
public final class AiLocatorHealer implements LocatorHealer {

    private static final Logger LOG = LoggerFactory.getLogger(AiLocatorHealer.class);

    record Candidate(String css, String reason) {
    }

    record Answer(List<Candidate> candidates) {
    }

    @Override
    public String name() {
        return AiClient.shared().map(c -> "ai:" + c.settings().describe()).orElse("ai");
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public List<By> candidates(HealingRequest request) {
        if (!AutomationConfig.get().getBoolean("ai.healing.enabled", true)) {
            return List.of();
        }
        return AiClient.shared().map(ai -> {
            String prompt = Prompts.render("locator-healing", Map.of(
                    "description", request.locator().description(),
                    "brokenLocator", request.locator().by().toString(),
                    "url", request.url(),
                    "dom", ai.prepare(DomCompactor.compact(request.pageSource()))));
            Answer answer;
            try {
                answer = ai.json(Prompts.render("locator-healing-system", Map.of()), prompt, null, Answer.class);
            } catch (RuntimeException e) {
                LOG.warn("AI healer got no usable answer for '{}': {}", request.locator().description(), e.getMessage());
                return List.<By>of();
            }
            if (answer.candidates() == null) {
                return List.<By>of();
            }
            List<By> result = answer.candidates().stream()
                    .filter(c -> c.css() != null && !c.css().isBlank())
                    .map(c -> (By) By.cssSelector(c.css().trim()))
                    .toList();
            LOG.info("AI proposed {} selector(s) for '{}'", result.size(), request.locator().description());
            return result;
        }).orElse(List.of());
    }
}
