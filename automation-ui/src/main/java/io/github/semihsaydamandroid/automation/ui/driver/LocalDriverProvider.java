package io.github.semihsaydamandroid.automation.ui.driver;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

/** Starts a browser on this machine. Selenium Manager downloads matching drivers when needed. */
public final class LocalDriverProvider implements DriverProvider {

    @Override
    public ExecutionTarget target() {
        return ExecutionTarget.LOCAL;
    }

    @Override
    public DriverSession create(UiSettings settings, Capabilities capabilities) {
        WebDriver driver = switch (settings.browser()) {
            case CHROME -> new ChromeDriver((ChromeOptions) capabilities);
            case FIREFOX -> new FirefoxDriver((FirefoxOptions) capabilities);
            case EDGE -> new EdgeDriver((EdgeOptions) capabilities);
            case SAFARI -> new SafariDriver((SafariOptions) capabilities);
        };
        return DriverSession.of(driver, settings.browser().name().toLowerCase() + "@local");
    }
}
