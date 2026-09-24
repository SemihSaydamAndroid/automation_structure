package io.github.semihsaydamandroid.automation.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import com.sun.net.httpserver.HttpServer;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.spi.FailureAnalysis;
import io.github.semihsaydamandroid.automation.ui.driver.DriverManager;
import io.github.semihsaydamandroid.automation.ui.element.Locator;
import io.github.semihsaydamandroid.automation.ui.element.UiElement;
import io.github.semihsaydamandroid.automation.ui.healing.HealingReport;
import io.github.semihsaydamandroid.automation.ui.junit.Evidence;
import io.github.semihsaydamandroid.automation.ui.junit.UiTest;
import io.github.semihsaydamandroid.automation.ui.page.BasePage;

/**
 * Real browser test (tag "ui", run with -Pe2e). Uses ui.execution from configuration, e.g.
 * {@code -Dui.execution=remote -Dui.grid-url=http://localhost:4444} or a local Chrome.
 */
@UiTest
class UiElementE2ETest {

    private static HttpServer server;

    @BeforeAll
    static void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = "pages" + exchange.getRequestURI().getPath();
            try (InputStream in = UiElementE2ETest.class.getClassLoader().getResourceAsStream(path)) {
                byte[] body = in == null ? new byte[0] : in.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(in == null ? 404 : 200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        server.start();
        AutomationConfig.set(AutomationConfig.load().with(Map.of(
                "ui.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                "ui.headless", "true",
                "ui.timeout.explicit", "5s")));
    }

    @AfterAll
    static void stop() {
        server.stop(0);
        AutomationConfig.reset();
    }

    static class LoginPage extends BasePage {
        final UiElement username = $(Locator.testId("username", "Username field"));
        final UiElement password = $(Locator.testId("password", "Password field"));
        // Outdated locator on purpose: the page renamed the id; the description lets healing recover.
        final UiElement signIn = $(Locator.id("login-btn", "Sign in"));
        final UiElement greeting = $(By.id("greeting"), "Greeting");

        LoginPage(WebDriver driver) {
            super(driver);
        }

        LoginPage open() {
            open("/login.html");
            return this;
        }
    }

    @Test
    void waitsForLateRenderingAndOverlaysAndHealsOutdatedLocators(WebDriver driver) {
        LoginPage page = new LoginPage(driver).open();

        page.username.type("semih");
        page.password.type("s3cret");
        page.signIn.click();

        page.greeting.shouldHaveText("Welcome, semih");
        assertThat(HealingReport.entries()).anySatisfy(entry -> {
            assertThat(entry.original()).contains("login-btn");
            assertThat(entry.healed()).contains("Sign in");
        });
    }

    @Test
    void failuresAreTriagedWithBrowserEvidence(WebDriver driver) {
        new LoginPage(driver).open();
        Throwable failure = new org.openqa.selenium.NoSuchElementException("Unable to locate element: #checkout");

        var analysis = Evidence.captureFailure("checkout flow", failure, DriverManager.session().orElseThrow());

        assertThat(analysis).hasValueSatisfying(a ->
                assertThat(a.category()).isEqualTo(FailureAnalysis.Category.LOCATOR_CHANGED));
    }

    @Test
    void assertionFailuresAreDescriptive(WebDriver driver) {
        LoginPage page = new LoginPage(driver).open();

        assertThatThrownBy(() -> page.greeting.shouldHaveText("Welcome, nobody"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("'Greeting'")
                .hasMessageContaining("to have text 'Welcome, nobody'");
    }
}
