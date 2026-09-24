package io.github.semihsaydamandroid.automation.ui.driver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A running browser plus whatever must be released with it (a Kubernetes pod, a port-forward).
 *
 * @param driver      the (decorated) driver tests interact with
 * @param description human readable origin, e.g. {@code chrome@pod/qa-local/browser-abc}
 * @param release     cleanup executed after {@link WebDriver#quit()}
 * @param consoleLog  browser console entries collected over WebDriver BiDi when {@code ui.bidi=true}
 */
public record DriverSession(WebDriver driver, String description, AutoCloseable release, List<String> consoleLog)
        implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DriverSession.class);

    public static DriverSession of(WebDriver driver, String description) {
        return of(driver, description, () -> { });
    }

    public static DriverSession of(WebDriver driver, String description, AutoCloseable release) {
        return new DriverSession(driver, description, release, new CopyOnWriteArrayList<>());
    }

    public DriverSession withDriver(WebDriver decorated) {
        return new DriverSession(decorated, description, release, consoleLog);
    }

    @Override
    public void close() {
        try {
            driver.quit();
        } catch (RuntimeException e) {
            LOG.warn("Quitting {} failed: {}", description, e.toString());
        } finally {
            try {
                release.close();
            } catch (Exception e) {
                LOG.warn("Releasing {} failed: {}", description, e.toString());
            }
        }
    }
}
