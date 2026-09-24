package io.github.semihsaydamandroid.automation.ui.driver;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.Dimension;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/** Typed view of the {@code ui.*} configuration. */
public record UiSettings(
        Browser browser,
        ExecutionTarget execution,
        boolean headless,
        String gridUrl,
        String baseUrl,
        Dimension windowSize,
        Duration explicitTimeout,
        Duration pageLoadTimeout,
        Duration scriptTimeout,
        Duration pollInterval,
        Duration remoteReadTimeout,
        boolean acceptInsecureCerts,
        boolean bidi,
        boolean logActions,
        List<String> arguments,
        Map<String, String> capabilities) {

    public static UiSettings from(AutomationConfig config) {
        return new UiSettings(
                config.getEnum("ui.browser", Browser.class, Browser.CHROME),
                config.getEnum("ui.execution", ExecutionTarget.class, ExecutionTarget.LOCAL),
                config.getBoolean("ui.headless", false),
                config.get("ui.grid-url", "http://localhost:4444"),
                config.get("ui.base-url", ""),
                parseSize(config.get("ui.window-size", "1920x1080")),
                config.getDuration("ui.timeout.explicit", Duration.ofSeconds(10)),
                config.getDuration("ui.timeout.page-load", Duration.ofSeconds(60)),
                config.getDuration("ui.timeout.script", Duration.ofSeconds(30)),
                config.getDuration("ui.poll-interval", Duration.ofMillis(200)),
                config.getDuration("ui.timeout.remote-read", Duration.ofMinutes(3)),
                config.getBoolean("ui.accept-insecure-certs", true),
                config.getBoolean("ui.bidi", false),
                config.getBoolean("ui.log-actions", true),
                config.getList("ui.arguments"),
                config.section("ui.capabilities"));
    }

    static Dimension parseSize(String value) {
        String[] parts = value.toLowerCase().split("x");
        if (parts.length != 2) {
            throw new IllegalArgumentException("ui.window-size must look like 1920x1080 but was " + value);
        }
        return new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }
}
