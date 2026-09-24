package io.github.semihsaydamandroid.automation.ui.element;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;

import io.github.semihsaydamandroid.automation.ui.healing.LocatorHealing;
import io.github.semihsaydamandroid.automation.ui.wait.Waits;

/**
 * Lazy, auto-waiting element handle. Every action re-locates the element, waits for the state the
 * action needs and retries on transient failures (stale element, click intercepted), so tests
 * never need sleeps or implicit waits. When the locator stops matching, the self-healing chain is
 * consulted before failing.
 */
public class UiElement {

    private final WebDriver driver;
    private final Locator locator;
    private final Waits waits;
    private volatile By effective;

    public UiElement(WebDriver driver, Locator locator, Waits waits) {
        this.driver = driver;
        this.locator = locator;
        this.waits = waits;
        this.effective = LocatorHealing.cached(locator.by()).orElse(locator.by());
    }

    public Locator locator() {
        return locator;
    }

    // ------------------------------------------------------------------ actions

    public UiElement click() {
        act("click", element -> {
            element.click();
            return true;
        });
        return this;
    }

    public UiElement type(CharSequence text) {
        act("type into", element -> {
            element.clear();
            element.sendKeys(text);
            return true;
        });
        return this;
    }

    public UiElement append(CharSequence text) {
        act("type into", element -> {
            element.sendKeys(text);
            return true;
        });
        return this;
    }

    public UiElement clear() {
        act("clear", element -> {
            element.clear();
            return true;
        });
        return this;
    }

    public UiElement selectByText(String visibleText) {
        act("select '" + visibleText + "' in", element -> {
            new Select(element).selectByVisibleText(visibleText);
            return true;
        });
        return this;
    }

    public UiElement hover() {
        new Actions(driver).moveToElement(visible()).perform();
        return this;
    }

    public UiElement scrollIntoView() {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", present());
        return this;
    }

    // ------------------------------------------------------------------ queries

    public String text() {
        return visible().getText();
    }

    public String value() {
        return present().getDomProperty("value");
    }

    public String attribute(String name) {
        return present().getDomAttribute(name);
    }

    /** Immediate check without waiting. */
    public boolean isVisible() {
        List<WebElement> found = driver.findElements(effective);
        return !found.isEmpty() && found.get(0).isDisplayed();
    }

    /** Immediate check without waiting. */
    public boolean exists() {
        return !driver.findElements(effective).isEmpty();
    }

    public int count() {
        return driver.findElements(effective).size();
    }

    public List<WebElement> all() {
        return driver.findElements(effective);
    }

    // ------------------------------------------------------------------ assertions

    public UiElement shouldBeVisible() {
        assertThat(d -> isVisible(), "to be visible");
        return this;
    }

    public UiElement shouldBeHidden() {
        assertThat(d -> !isVisible(), "to be hidden");
        return this;
    }

    public UiElement shouldHaveText(String expected) {
        assertThat(d -> expected.equals(currentText()), "to have text '" + expected + "' but was '%s'");
        return this;
    }

    public UiElement shouldContainText(String expected) {
        assertThat(d -> currentText().contains(expected), "to contain text '" + expected + "' but was '%s'");
        return this;
    }

    public UiElement shouldHaveValue(String expected) {
        assertThat(d -> expected.equals(present().getDomProperty("value")), "to have value '" + expected + "'");
        return this;
    }

    // ------------------------------------------------------------------ resolution

    /** The element once present in the DOM (healing if necessary). */
    public WebElement present() {
        return resolve(by -> driver -> {
            List<WebElement> found = driver.findElements(by);
            return found.isEmpty() ? null : found.get(0);
        }, "presence");
    }

    /** The element once displayed (healing if necessary). */
    public WebElement visible() {
        return resolve(by -> driver -> {
            List<WebElement> found = driver.findElements(by);
            return !found.isEmpty() && found.get(0).isDisplayed() ? found.get(0) : null;
        }, "visibility");
    }

    private WebElement resolve(Function<By, Function<WebDriver, WebElement>> condition, String state) {
        try {
            return waits.until(condition.apply(effective), state + " of " + locator);
        } catch (TimeoutException original) {
            Optional<By> healed = LocatorHealing.heal(driver, locator);
            if (healed.isEmpty()) {
                throw original;
            }
            effective = healed.get();
            return waits.until(condition.apply(effective), state + " of " + locator + " (healed: " + effective + ")");
        }
    }

    private void act(String action, Function<WebElement, Boolean> interaction) {
        visible();
        waits.until(d -> {
            try {
                WebElement element = d.findElement(effective);
                return element.isDisplayed() && element.isEnabled() && interaction.apply(element);
            } catch (ElementNotInteractableException e) { // includes ElementClickInterceptedException
                return false; // overlay or animation; retry until timeout
            }
        }, action + " " + locator);
    }

    private String currentText() {
        List<WebElement> found = driver.findElements(effective);
        return found.isEmpty() ? "<absent>" : found.get(0).getText();
    }

    private void assertThat(Function<WebDriver, Boolean> condition, String expectation) {
        try {
            waits.until(d -> condition.apply(d) ? Boolean.TRUE : null, locator + " " + expectation);
        } catch (TimeoutException e) {
            throw new AssertionError("Expected " + locator + " " + expectation.replace("%s", currentText())
                    + " within " + waits.timeout().toMillis() + "ms", e);
        }
    }

    @Override
    public String toString() {
        return locator.toString();
    }
}
