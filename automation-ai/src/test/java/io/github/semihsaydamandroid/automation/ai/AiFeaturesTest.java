package io.github.semihsaydamandroid.automation.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import io.github.semihsaydamandroid.automation.ai.analysis.AiFailureAnalyzer;
import io.github.semihsaydamandroid.automation.ai.healing.AiLocatorHealer;
import io.github.semihsaydamandroid.automation.ai.model.AiClient;
import io.github.semihsaydamandroid.automation.ai.model.AiProvider;
import io.github.semihsaydamandroid.automation.ai.model.AiSettings;
import io.github.semihsaydamandroid.automation.ai.model.DomCompactor;
import io.github.semihsaydamandroid.automation.ai.model.Redactor;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.spi.FailureAnalysis;
import io.github.semihsaydamandroid.automation.core.spi.FailureAnalyzers;
import io.github.semihsaydamandroid.automation.core.spi.FailureContext;
import io.github.semihsaydamandroid.automation.ui.element.Locator;
import io.github.semihsaydamandroid.automation.ui.healing.HealingRequest;

class AiFeaturesTest {

    private final FakeChatModel model = new FakeChatModel();

    private void useFake() {
        AiSettings settings = new AiSettings(AiProvider.OLLAMA, "llama3.1:8b", "llama3.1:8b", "http://localhost:11434",
                "", 0.0, 512, Duration.ofSeconds(5), true, 10_000);
        AiClient.use(new AiClient(settings, model, model));
    }

    @AfterEach
    void reset() {
        AiClient.use(null);
        AutomationConfig.reset();
    }

    @Test
    void failureAnalysisParsesSloppyModelOutputAndRedactsEvidence() {
        useFake();
        model.answer("""
                Sure! Here is my analysis:
                ```json
                {"category": "environment", "confidence": 0.85, "summary": "Payment service down",
                 "rootCause": "503 from /payments", "suggestedFix": "Check payments deployment", "extra": 1}
                ```""");
        FailureContext context = new FailureContext("checkout", "api", null,
                "status code was: 503, expected: 200",
                Map.of("httpLog", "Authorization: Bearer eyJhbGciOi.secret.token\n{\"email\":\"ayse@example.com\",\"tckn\":\"12345678950\"}"),
                null);

        FailureAnalysis analysis = new AiFailureAnalyzer().analyze(context).orElseThrow();

        assertThat(analysis.category()).isEqualTo(FailureAnalysis.Category.ENVIRONMENT);
        assertThat(analysis.confidence()).isEqualTo(0.85);
        assertThat(analysis.analyzer()).isEqualTo("ai:ollama/llama3.1:8b");
        String prompt = model.lastPrompt();
        assertThat(prompt).contains("status code was: 503", "Rule-based hint: ENVIRONMENT");
        assertThat(prompt).doesNotContain("eyJhbGciOi", "ayse@example.com", "12345678950");
    }

    @Test
    void modelOutagesFallBackToRules() {
        useFake();
        model.failure = new RuntimeException("connection refused: localhost:11434");
        FailureContext context = new FailureContext("login", "ui", new AssertionError("expected: <Welcome>"), null, Map.of(), null);

        assertThat(new AiFailureAnalyzer().analyze(context)).isEmpty();
        assertThat(FailureAnalyzers.discover().analyze(context)).hasValueSatisfying(a ->
                assertThat(a.analyzer()).isEqualTo("rules"));
    }

    @Test
    void invalidJsonIsRetriedOnce() {
        useFake();
        model.answer("I think it is a locator problem.")
                .answer("{\"candidates\":[{\"css\":\"button[data-testid='pay']\",\"reason\":\"text Pay\"},{\"css\":\" \"}]}");
        HealingRequest request = new HealingRequest(Locator.id("pay-now", "Pay button"), "https://shop/checkout",
                "<html><script>var x=1</script><button data-testid='pay' class='btn'>Pay</button></html>", null);

        List<By> candidates = new AiLocatorHealer().candidates(request);

        assertThat(candidates).containsExactly(By.cssSelector("button[data-testid='pay']"));
        assertThat(model.requests).hasSize(2);
        assertThat(model.lastPrompt()).doesNotContain("<script>");
    }

    @Test
    void disabledProviderMeansNoCallsAndNoCandidates() {
        AutomationConfig.set(AutomationConfig.of(Map.of("ai.provider", "none")));

        assertThat(AiClient.shared()).isEmpty();
        assertThat(new AiLocatorHealer().candidates(new HealingRequest(Locator.id("x", "X"), "", "", null))).isEmpty();
    }

    @Test
    void providerDefaults() {
        AiSettings ollama = AiSettings.from(AutomationConfig.of(Map.of("ai.provider", "ollama")));
        AiSettings claude = AiSettings.from(AutomationConfig.of(Map.of("ai.provider", "anthropic", "ai.api-key", "k")));
        AiSettings vllm = AiSettings.from(AutomationConfig.of(Map.of("ai.provider", "openai-compatible",
                "ai.base-url", "http://vllm.ai.svc:8000/v1", "ai.model", "Qwen/Qwen2.5-7B-Instruct")));

        assertThat(ollama.baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(ollama.model()).isEqualTo("llama3.1:8b");
        assertThat(claude.model()).isEqualTo("claude-sonnet-5");
        assertThat(vllm.provider()).isEqualTo(AiProvider.OPENAI_COMPATIBLE);
    }

    @Test
    void redactorAndDomCompactor() {
        assertThat(Redactor.redact("password=hunter2&user=x, card 4111 1111 1111 1111, iban TR33 0006 1005 1978 6457 8413 26, tel 0532 123 45 67"))
                .contains("password=<redacted>", "<card>", "<iban>", "<phone>")
                .doesNotContain("hunter2", "4111", "0532");
        assertThat(DomCompactor.compact("""
                <div style="color:red" onclick="x()" data-testid="cart"><!-- c --><style>.a{}</style>
                  <span class="badge" data-v-123="">2</span><svg><path d="M0"/></svg></div>"""))
                .isEqualTo("<div data-testid=\"cart\"><span class=\"badge\">2</span></div>");
    }
}
