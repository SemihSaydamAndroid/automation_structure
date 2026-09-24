package io.github.semihsaydamandroid.automation.bdd.steps.ui;

import java.time.Duration;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.semihsaydamandroid.automation.bdd.support.ByVisibleName;
import io.github.semihsaydamandroid.automation.bdd.support.ElementRegistry;
import io.github.semihsaydamandroid.automation.bdd.support.FailureCapture;
import io.github.semihsaydamandroid.automation.bdd.support.ScenarioContext;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.ui.driver.DriverManager;
import io.github.semihsaydamandroid.automation.ui.element.Locator;
import io.github.semihsaydamandroid.automation.ui.element.UiElement;
import io.github.semihsaydamandroid.automation.ui.junit.Evidence;
import io.github.semihsaydamandroid.automation.ui.wait.Waits;

/**
 * Generic UI sentences in English and Turkish. Every element argument is a business name,
 * resolved through the object repository ({@code elements/*.locators}) or by visible name.
 * Every value argument supports {@code ${variable}}, {@code ${config.key}} and {@code #{faker}}.
 * <p>
 * Keep feature files at the business level: use these for glue and prototyping, and write
 * domain sentences ("Given a customer with 2 items in the cart") in your own step classes.
 */
public class UiSteps {

    private final ScenarioContext context;

    public UiSteps(ScenarioContext context) {
        this.context = context;
    }

    // ------------------------------------------------------------------ navigation

    @Given("I open {string}")
    @Given("{string} sayfasını açarım")
    @Given("{string} adresine giderim")
    public void open(String path) {
        String url = context.resolve(path);
        if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            String base = AutomationConfig.get().get("ui.base-url");
            url = base.replaceAll("/+$", "") + "/" + url.replaceAll("^/+", "");
        }
        driver().get(url);
        waits().forPageLoad();
    }

    // ------------------------------------------------------------------ actions

    @When("I click {string}")
    @When("{string} butonuna/linkine/öğesine/düğmesine tıklarım")
    public void click(String element) {
        element(element).click();
    }

    @When("I type {string} into {string}")
    public void typeInto(String value, String element) {
        element(element).type(context.resolve(value));
    }

    @When("{string} alanına/kutusuna {string} yazarım")
    public void typeIntoTr(String element, String value) {
        typeInto(value, element);
    }

    @When("I clear {string}")
    @When("{string} alanını temizlerim")
    public void clear(String element) {
        element(element).clear();
    }

    @When("I select {string} from {string}")
    public void select(String option, String element) {
        element(element).selectByText(context.resolve(option));
    }

    @When("{string} listesinden {string} seçerim")
    public void selectTr(String element, String option) {
        select(option, element);
    }

    @When("I hover over {string}")
    @When("{string} üzerine gelirim")
    public void hover(String element) {
        element(element).hover();
    }

    @When("I fill in:")
    @When("aşağıdaki alanları doldururum:")
    public void fillIn(DataTable fields) {
        for (Map.Entry<String, String> field : fields.asMap(String.class, String.class).entrySet()) {
            typeInto(field.getValue(), field.getKey());
        }
    }

    @When("I remember the text of {string} as {string}")
    public void remember(String element, String variable) {
        context.set(variable, element(element).text());
    }

    @When("{string} metnini {string} olarak saklarım")
    public void rememberTr(String element, String variable) {
        remember(element, variable);
    }

    // ------------------------------------------------------------------ assertions

    @Then("I should see {string}")
    @Then("{string} metnini/yazısını görmeliyim")
    public void shouldSee(String textOrElement) {
        String expected = context.resolve(textOrElement);
        if (ElementRegistry.find(expected).isPresent()) {
            element(expected).shouldBeVisible();
            return;
        }
        try {
            waits().until(d -> d.findElement(By.tagName("body")).getText().contains(expected), "text '" + expected + "'");
        } catch (TimeoutException e) {
            throw new AssertionError("Expected to see '" + expected + "' on " + driver().getCurrentUrl(), e);
        }
    }

    @Then("I should not see {string}")
    @Then("{string} metnini/yazısını görmemeliyim")
    public void shouldNotSee(String text) {
        String unexpected = context.resolve(text);
        try {
            waits().until(d -> !d.findElement(By.tagName("body")).getText().contains(unexpected), "text '" + unexpected + "' to disappear");
        } catch (TimeoutException e) {
            throw new AssertionError("Did not expect to see '" + unexpected + "' on " + driver().getCurrentUrl(), e);
        }
    }

    @Then("{string} should be visible")
    @Then("{string} görünür olmalı")
    public void shouldBeVisible(String element) {
        element(element).shouldBeVisible();
    }

    @Then("{string} should not be visible")
    @Then("{string} görünür olmamalı")
    public void shouldBeHidden(String element) {
        element(element).shouldBeHidden();
    }

    @Then("{string} should have text {string}")
    @Then("{string} öğesinin metni {string} olmalı")
    public void shouldHaveText(String element, String text) {
        element(element).shouldHaveText(context.resolve(text));
    }

    @Then("{string} should contain {string}")
    @Then("{string} öğesi {string} içermeli")
    public void shouldContain(String element, String text) {
        element(element).shouldContainText(context.resolve(text));
    }

    @Then("{string} should have value {string}")
    @Then("{string} alanının değeri {string} olmalı")
    public void shouldHaveValue(String element, String value) {
        element(element).shouldHaveValue(context.resolve(value));
    }

    @Then("the page title should be {string}")
    @Then("sayfa başlığı {string} olmalı")
    public void titleShouldBe(String title) {
        String expected = context.resolve(title);
        try {
            waits().forTitle(expected);
        } catch (TimeoutException e) {
            throw new AssertionError("Expected title '" + expected + "' but was '" + driver().getTitle() + "'", e);
        }
    }

    @Then("the URL should contain {string}")
    @Then("adres {string} içermeli")
    public void urlShouldContain(String fragment) {
        String expected = context.resolve(fragment);
        try {
            waits().forUrlContaining(expected);
        } catch (TimeoutException e) {
            throw new AssertionError("Expected URL containing '" + expected + "' but was " + driver().getCurrentUrl(), e);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Evidence and failure analysis for failed scenarios; the browser is always closed. */
    @After(order = 100)
    public void afterScenario(Scenario scenario) {
        try {
            if (scenario.isFailed()) {
                DriverManager.session().ifPresent(session -> {
                    try {
                        byte[] png = ((TakesScreenshot) session.driver()).getScreenshotAs(OutputType.BYTES);
                        scenario.attach(png, "image/png", "Screenshot");
                    } catch (RuntimeException ignored) {
                        // browser already gone; Evidence logs details
                    }
                    Evidence.captureFailure(scenario.getName(), FailureCapture.lastError().orElse(null), session)
                            .ifPresent(a -> scenario.log(a.toMarkdown()));
                });
            }
        } finally {
            DriverManager.quit();
        }
    }

    // ------------------------------------------------------------------ helpers

    protected UiElement element(String name) {
        String resolved = context.resolve(name);
        Locator locator = ElementRegistry.find(resolved).orElseGet(() -> Locator.of(new ByVisibleName(resolved), resolved));
        return new UiElement(driver(), locator, waits());
    }

    protected WebDriver driver() {
        return DriverManager.start();
    }

    protected Waits waits() {
        return Waits.of(driver());
    }

    protected Duration timeout() {
        return waits().timeout();
    }
}
