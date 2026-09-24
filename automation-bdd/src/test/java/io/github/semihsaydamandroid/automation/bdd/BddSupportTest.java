package io.github.semihsaydamandroid.automation.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import io.github.semihsaydamandroid.automation.bdd.support.ElementRegistry;
import io.github.semihsaydamandroid.automation.bdd.support.ScenarioContext;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

class BddSupportTest {

    @AfterEach
    void reset() {
        AutomationConfig.reset();
    }

    @Test
    void objectRepositoryMapsBusinessNamesCaseInsensitively() {
        assertThat(ElementRegistry.find("kullanıcı ADI")).hasValueSatisfying(l -> {
            assertThat(l.by()).isEqualTo(By.cssSelector("[data-testid='username']"));
            assertThat(l.description()).isEqualTo("Kullanıcı adı");
        });
        assertThat(ElementRegistry.find("Giriş")).hasValueSatisfying(l -> assertThat(l.by()).isEqualTo(By.id("submit")));
        assertThat(ElementRegistry.find("Unknown")).isEmpty();
    }

    @Test
    void valuesResolveVariablesConfigurationAndFakerExpressions() {
        AutomationConfig.set(AutomationConfig.of(Map.of("ui.base-url", "https://shop.test")));
        ScenarioContext context = new ScenarioContext();
        context.set("orderId", 42);

        assertThat(context.resolve("order ${orderId} on ${ui.base-url}")).isEqualTo("order 42 on https://shop.test");
        assertThat(context.resolve("#{unique:qa}")).matches("qa-[0-9a-f]{8}");
        assertThat(context.resolve("#{Name.firstName}")).isNotBlank().doesNotContain("#{");
        assertThat(context.resolve("plain text")).isEqualTo("plain text");
    }
}
