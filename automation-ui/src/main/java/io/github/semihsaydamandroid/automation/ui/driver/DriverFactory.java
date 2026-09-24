package io.github.semihsaydamandroid.automation.ui.driver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.module.LogInspector;
import org.openqa.selenium.support.events.EventFiringDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.core.util.ServiceLoaders;

/** Picks the {@link DriverProvider} for the configured target and prepares the session. */
public final class DriverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DriverFactory.class);

    private final Map<ExecutionTarget, DriverProvider> providers = new EnumMap<>(ExecutionTarget.class);

    public DriverFactory() {
        this(discover());
    }

    public DriverFactory(List<DriverProvider> providers) {
        providers.forEach(p -> this.providers.put(p.target(), p));
    }

    private static List<DriverProvider> discover() {
        List<DriverProvider> all = new ArrayList<>();
        all.add(new LocalDriverProvider());
        all.add(new RemoteDriverProvider());
        all.addAll(ServiceLoaders.load(DriverProvider.class)); // later entries override built-ins
        return all;
    }

    public DriverSession create(UiSettings settings) {
        DriverProvider provider = providers.get(settings.execution());
        if (provider == null) {
            throw new IllegalStateException("No driver provider for ui.execution=" + settings.execution()
                    + (settings.execution() == ExecutionTarget.KUBERNETES
                            ? ". Add the io.github.semihsaydamandroid:automation-k8s dependency."
                            : ""));
        }
        Capabilities capabilities = BrowserOptionsFactory.create(settings);
        LOG.info("Starting {} via {} (headless={})", settings.browser(), settings.execution(), settings.headless());
        DriverSession session = provider.create(settings, capabilities);
        WebDriver raw = session.driver();
        configure(raw, settings);
        if (settings.bidi()) {
            captureConsole(raw, session);
        }
        WebDriver decorated = settings.logActions()
                ? new EventFiringDecorator<>(new ActionLoggingListener()).decorate(raw)
                : raw;
        LOG.info("Browser session ready: {}", session.description());
        return session.withDriver(decorated);
    }

    private static void captureConsole(WebDriver driver, DriverSession session) {
        try {
            LogInspector inspector = new LogInspector(driver);
            inspector.onConsoleEntry(entry -> session.consoleLog().add(entry.getLevel() + " " + entry.getText()));
            inspector.onJavaScriptLog(entry -> session.consoleLog().add("JS " + entry.getLevel() + " " + entry.getText()));
        } catch (RuntimeException e) {
            LOG.warn("BiDi console capture unavailable for {}: {}", session.description(), e.toString());
        }
    }

    private static void configure(WebDriver driver, UiSettings settings) {
        WebDriver.Timeouts timeouts = driver.manage().timeouts();
        timeouts.pageLoadTimeout(settings.pageLoadTimeout());
        timeouts.scriptTimeout(settings.scriptTimeout());
        // Implicit waits stay at zero: explicit waits in UiElement/Waits are the only waiting strategy.
        timeouts.implicitlyWait(Duration.ZERO);
        if (settings.browser() != Browser.CHROME && settings.browser() != Browser.EDGE) {
            driver.manage().window().setSize(settings.windowSize());
        }
    }
}
