package io.github.semihsaydamandroid.automation.ui.healing;

import org.openqa.selenium.WebDriver;

import io.github.semihsaydamandroid.automation.ui.element.Locator;

/**
 * Input for {@link LocatorHealer}s.
 *
 * @param locator    the locator that no longer matches
 * @param url        current page URL
 * @param pageSource current DOM
 * @param driver     live driver, for healers that want to probe the page
 */
public record HealingRequest(Locator locator, String url, String pageSource, WebDriver driver) {
}
