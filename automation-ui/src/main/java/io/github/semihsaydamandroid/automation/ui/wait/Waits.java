package io.github.semihsaydamandroid.automation.ui.wait;

import java.time.Duration;
import java.util.function.Function;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.FluentWait;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.ui.driver.UiSettings;

/** Explicit waits with the configured timeout and polling interval. */
public final class Waits {

    private final WebDriver driver;
    private final Duration timeout;
    private final Duration pollInterval;

    public Waits(WebDriver driver, Duration timeout, Duration pollInterval) {
        this.driver = driver;
        this.timeout = timeout;
        this.pollInterval = pollInterval;
    }

    public static Waits of(WebDriver driver) {
        UiSettings settings = UiSettings.from(AutomationConfig.get());
        return new Waits(driver, settings.explicitTimeout(), settings.pollInterval());
    }

    public Waits withTimeout(Duration newTimeout) {
        return new Waits(driver, newTimeout, pollInterval);
    }

    public Duration timeout() {
        return timeout;
    }

    /** Polls until the condition returns a non-null, non-false value. */
    public <T> T until(Function<WebDriver, T> condition, String description) {
        return new FluentWait<>(driver)
                .withTimeout(timeout)
                .pollingEvery(pollInterval)
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class)
                .withMessage(() -> "waiting " + timeout.toMillis() + "ms for " + description)
                .until(condition);
    }

    public void forPageLoad() {
        until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")),
                "document.readyState == complete");
    }

    public void forUrlContaining(String fragment) {
        until(d -> {
            String url = d.getCurrentUrl();
            return url != null && url.contains(fragment);
        }, "URL containing '" + fragment + "'");
    }

    public void forTitle(String title) {
        until(d -> title.equals(d.getTitle()), "title '" + title + "'");
    }
}
