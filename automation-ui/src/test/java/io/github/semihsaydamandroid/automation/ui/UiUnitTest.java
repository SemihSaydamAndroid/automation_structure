package io.github.semihsaydamandroid.automation.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.ui.driver.BrowserOptionsFactory;
import io.github.semihsaydamandroid.automation.ui.driver.DriverFactory;
import io.github.semihsaydamandroid.automation.ui.driver.ExecutionTarget;
import io.github.semihsaydamandroid.automation.ui.driver.UiSettings;
import io.github.semihsaydamandroid.automation.ui.element.Locator;
import io.github.semihsaydamandroid.automation.ui.healing.HealingRequest;
import io.github.semihsaydamandroid.automation.ui.healing.HeuristicLocatorHealer;

class UiUnitTest {

    @Test
    void chromeOptionsFollowSettings() {
        UiSettings settings = UiSettings.from(AutomationConfig.of(Map.of(
                "ui.browser", "chrome", "ui.headless", "true", "ui.window-size", "1280x720", "ui.bidi", "true",
                "ui.arguments", "--lang=tr-TR",
                "ui.capabilities.se:recordVideo", "true", "ui.capabilities.se:screenResolution", "1280x720")));

        AbstractDriverOptions<?> options = BrowserOptionsFactory.create(settings);

        assertThat(options).isInstanceOf(ChromeOptions.class);
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) ((Map<String, Object>) options.asMap().get("goog:chromeOptions")).get("args");
        assertThat(args).contains("--headless=new", "--window-size=1280,720", "--lang=tr-TR");
        assertThat(options.getCapability("webSocketUrl")).isEqualTo(true);
        assertThat(options.getCapability("se:recordVideo")).isEqualTo(true);
        assertThat(options.getCapability("se:screenResolution")).isEqualTo("1280x720");
    }

    @Test
    void kubernetesTargetExplainsMissingModule() {
        UiSettings settings = UiSettings.from(AutomationConfig.of(Map.of("ui.execution", "kubernetes")));

        assertThat(settings.execution()).isEqualTo(ExecutionTarget.KUBERNETES);
        assertThatThrownBy(() -> new DriverFactory().create(settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("automation-k8s");
    }

    @Test
    void heuristicHealerDerivesCandidatesFromLocatorAndIntent() {
        HeuristicLocatorHealer healer = new HeuristicLocatorHealer();
        HealingRequest request = new HealingRequest(Locator.id("login-btn", "Giriş butonu"), "", "", null);

        List<By> candidates = healer.candidates(request);

        assertThat(candidates).contains(
                By.cssSelector("[data-testid='login-btn']"),
                By.name("login-btn"),
                By.xpath("//button[normalize-space(.)='Giriş butonu']"),
                By.xpath("//button[normalize-space(.)='Giriş']"));
        assertThat(candidates).doesNotContain(By.id("login-btn"));
    }

    @Test
    void xpathLiteralsHandleQuotes() {
        assertThat(Locator.xpathLiteral("O'Reilly")).isEqualTo("\"O'Reilly\"");
        assertThat(Locator.xpathLiteral("say \"hi\" it's")).isEqualTo("concat('say \"hi\" it',\"'\",'s')");
    }
}
