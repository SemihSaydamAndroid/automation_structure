package io.github.semihsaydamandroid.automation.ui.driver;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.openqa.selenium.WebDriver;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/**
 * Thread-confined browser sessions, so JUnit/TestNG parallel execution is safe.
 * <p>
 * A shutdown hook quits sessions still open when the JVM stops (Ctrl+C, crashed fork). For
 * Kubernetes sessions that also deletes the browser pods; pods additionally carry an
 * {@code activeDeadlineSeconds} as a last line of defense.
 */
public final class DriverManager {

    private static final ThreadLocal<DriverSession> CURRENT = new ThreadLocal<>();
    private static final Set<DriverSession> OPEN = ConcurrentHashMap.newKeySet();
    private static volatile DriverFactory factory;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> OPEN.forEach(DriverSession::close),
                "automation-driver-cleanup"));
    }

    private DriverManager() {
    }

    /** Starts a session for this thread unless one is running, and returns its driver. */
    public static WebDriver start() {
        return start(UiSettings.from(AutomationConfig.get()));
    }

    public static WebDriver start(UiSettings settings) {
        DriverSession session = CURRENT.get();
        if (session == null) {
            session = factory().create(settings);
            CURRENT.set(session);
            OPEN.add(session);
        }
        return session.driver();
    }

    /** The driver of this thread; fails fast when no session was started. */
    public static WebDriver driver() {
        DriverSession session = CURRENT.get();
        if (session == null) {
            throw new IllegalStateException("No browser session on thread " + Thread.currentThread().getName()
                    + ". Call DriverManager.start() or annotate the test with @UiTest.");
        }
        return session.driver();
    }

    public static Optional<DriverSession> session() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static boolean isStarted() {
        return CURRENT.get() != null;
    }

    public static void quit() {
        DriverSession session = CURRENT.get();
        if (session != null) {
            CURRENT.remove();
            OPEN.remove(session);
            session.close();
        }
    }

    /** Overrides provider discovery, e.g. to inject a custom provider in tests. */
    public static synchronized void useFactory(DriverFactory custom) {
        factory = custom;
    }

    private static DriverFactory factory() {
        DriverFactory result = factory;
        if (result == null) {
            synchronized (DriverManager.class) {
                if (factory == null) {
                    factory = new DriverFactory();
                }
                result = factory;
            }
        }
        return result;
    }
}
