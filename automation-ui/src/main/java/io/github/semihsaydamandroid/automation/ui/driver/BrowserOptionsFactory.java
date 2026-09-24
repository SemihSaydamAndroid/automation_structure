package io.github.semihsaydamandroid.automation.ui.driver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.safari.SafariOptions;

/** Builds browser options from {@link UiSettings}; identical for local, grid and pod sessions. */
public final class BrowserOptionsFactory {

    private BrowserOptionsFactory() {
    }

    public static AbstractDriverOptions<?> create(UiSettings settings) {
        AbstractDriverOptions<?> options = switch (settings.browser()) {
            case CHROME -> chromium(new ChromeOptions(), settings);
            case EDGE -> chromium(new EdgeOptions(), settings);
            case FIREFOX -> firefox(settings);
            case SAFARI -> new SafariOptions();
        };
        options.setAcceptInsecureCerts(settings.acceptInsecureCerts());
        if (settings.bidi()) {
            options.setCapability("webSocketUrl", true);
        }
        applyExtraCapabilities(options, settings.capabilities());
        return options;
    }

    private static <T extends ChromiumOptions<T>> T chromium(T options, UiSettings settings) {
        List<String> args = new ArrayList<>(List.of(
                "--window-size=" + settings.windowSize().getWidth() + "," + settings.windowSize().getHeight(),
                "--disable-search-engine-choice-screen",
                "--disable-dev-shm-usage"));
        if (settings.headless()) {
            args.add("--headless=new");
        }
        args.addAll(settings.arguments());
        options.addArguments(args);
        return options;
    }

    private static FirefoxOptions firefox(UiSettings settings) {
        FirefoxOptions options = new FirefoxOptions();
        List<String> args = new ArrayList<>(List.of(
                "-width=" + settings.windowSize().getWidth(),
                "-height=" + settings.windowSize().getHeight()));
        if (settings.headless()) {
            args.add("-headless");
        }
        args.addAll(settings.arguments());
        options.addArguments(args);
        return options;
    }

    /**
     * {@code ui.capabilities.<name>=<value>} entries become capabilities; booleans and numbers are
     * converted so that e.g. {@code ui.capabilities.se:recordVideo=true} works on Grid.
     */
    static void applyExtraCapabilities(MutableCapabilities options, Map<String, String> extra) {
        extra.forEach((name, value) -> options.setCapability(name, convert(value)));
    }

    private static Object convert(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        if (value.matches("-?\\d+")) {
            return Long.parseLong(value);
        }
        return value;
    }
}
