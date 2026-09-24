package io.github.semihsaydamandroid.automation.ui.page;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.report.Reporter;
import io.github.semihsaydamandroid.automation.ui.driver.DriverManager;
import io.github.semihsaydamandroid.automation.ui.element.Locator;
import io.github.semihsaydamandroid.automation.ui.element.UiElement;
import io.github.semihsaydamandroid.automation.ui.wait.Waits;

/**
 * Base class for page objects and components.
 *
 * <pre>{@code
 * public class LoginPage extends BasePage {
 *     private final UiElement username = $(Locator.testId("username", "Username field"));
 *     private final UiElement submit   = $(Locator.testId("login", "Login button"));
 *
 *     public LoginPage open() { open("/login"); return this; }
 *     public HomePage loginAs(String user, String pass) { ... }
 * }
 * }</pre>
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final Waits waits;

    protected BasePage() {
        this(DriverManager.driver());
    }

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.waits = Waits.of(driver);
    }

    protected UiElement $(Locator locator) {
        return new UiElement(driver, locator, waits);
    }

    protected UiElement $(By by, String description) {
        return $(Locator.of(by, description));
    }

    /** Opens {@code path} relative to {@code ui.base-url} (absolute URLs are used as is). */
    protected void open(String path) {
        String url = resolveUrl(path);
        Reporter.step("Open " + url);
        driver.get(url);
        waits.forPageLoad();
    }

    public String title() {
        return driver.getTitle();
    }

    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    /** Absolute URLs are returned as is; relative paths are resolved against {@code ui.base-url}. */
    public static String resolveUrl(String path) {
        if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return path;
        }
        String base = AutomationConfig.get().get("ui.base-url", "");
        if (base.isEmpty()) {
            throw new IllegalStateException("ui.base-url is not configured; cannot open relative path " + path);
        }
        return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }
}
