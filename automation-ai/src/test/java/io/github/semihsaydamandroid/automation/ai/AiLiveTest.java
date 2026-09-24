package io.github.semihsaydamandroid.automation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import io.github.semihsaydamandroid.automation.ai.analysis.AiFailureAnalyzer;
import io.github.semihsaydamandroid.automation.ai.generation.AiTestData;
import io.github.semihsaydamandroid.automation.ai.healing.AiLocatorHealer;
import io.github.semihsaydamandroid.automation.ai.model.AiClient;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.spi.FailureAnalysis;
import io.github.semihsaydamandroid.automation.core.spi.FailureContext;
import io.github.semihsaydamandroid.automation.ui.element.Locator;
import io.github.semihsaydamandroid.automation.ui.healing.HealingRequest;

/**
 * Against a real model; excluded by default. Example:
 * {@code mvn test -pl automation-ai -Dautomation.excludedGroups=k8s -Dai.provider=ollama -Dai.model=llama3.2:1b}
 */
@Tag("ai-live")
class AiLiveTest {

    @BeforeEach
    void requireProvider() {
        AutomationConfig.reset();
        AiClient.use(null);
        assumeTrue(AiClient.shared().isPresent(), "set ai.provider to run live AI tests");
    }

    @Test
    void triagesAnEnvironmentFailure() {
        FailureContext context = new FailureContext("checkout pays order", "api", null,
                "status code was: 503, expected: 201, response time: 30012ms",
                Map.of("httpLog", "POST https://api.staging.example.com/payments\n< 503 Service Unavailable\n"
                        + "{\"error\":\"upstream payment-gateway timed out\"}"),
                null);

        FailureAnalysis analysis = new AiFailureAnalyzer().analyze(context).orElseThrow();

        System.out.println(analysis.toMarkdown());
        assertThat(analysis.category()).isNotNull();
        assertThat(analysis.summary()).isNotBlank();
        assertThat(analysis.analyzer()).startsWith("ai:");
    }

    @Test
    void proposesSelectorsForAChangedElement() {
        String dom = """
                <html><body><form>
                  <input data-testid="email" placeholder="E-posta">
                  <input data-testid="pass" type="password">
                  <button data-testid="sign-in-button" class="btn primary">Giriş yap</button>
                </form></body></html>""";

        List<By> candidates = new AiLocatorHealer().candidates(
                new HealingRequest(Locator.id("login-btn", "Giriş butonu"), "https://shop/login", dom, null));

        System.out.println("AI candidates: " + candidates);
        assertThat(candidates).isNotEmpty();
    }

    @Test
    void generatesStructuredTestData() {
        List<Map<String, Object>> customers = AiTestData.generate("Turkish retail customers with firstName, lastName and city", 3);

        System.out.println(customers);
        assertThat(customers).isNotEmpty();
        assertThat(customers.get(0)).isNotEmpty();
    }
}
