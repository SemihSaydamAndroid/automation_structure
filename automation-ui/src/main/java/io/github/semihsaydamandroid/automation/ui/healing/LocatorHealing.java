package io.github.semihsaydamandroid.automation.ui.healing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.report.Reporter;
import io.github.semihsaydamandroid.automation.core.util.ServiceLoaders;
import io.github.semihsaydamandroid.automation.ui.element.Locator;

/** Runs the {@link LocatorHealer} chain and validates candidates against the live page. */
public final class LocatorHealing {

    private static final Logger LOG = LoggerFactory.getLogger(LocatorHealing.class);
    private static final Map<By, By> CACHE = new ConcurrentHashMap<>();
    private static volatile List<LocatorHealer> healers;

    private LocatorHealing() {
    }

    public static boolean enabled() {
        return AutomationConfig.get().getBoolean("ui.healing.enabled", true);
    }

    /** A previously healed replacement for this locator, if any. */
    public static Optional<By> cached(By original) {
        return Optional.ofNullable(CACHE.get(original));
    }

    public static Optional<By> heal(WebDriver driver, Locator locator) {
        if (!enabled()) {
            return Optional.empty();
        }
        String url = safe(driver::getCurrentUrl);
        String source = safe(driver::getPageSource);
        HealingRequest request = new HealingRequest(locator, url, source, driver);
        for (LocatorHealer healer : healers()) {
            List<By> candidates;
            try {
                candidates = healer.candidates(request);
            } catch (RuntimeException e) {
                LOG.warn("Healer {} failed: {}", healer.name(), e.toString());
                continue;
            }
            for (By candidate : candidates) {
                if (matchesExactlyOneDisplayed(driver, candidate)) {
                    CACHE.put(locator.by(), candidate);
                    HealingReport.add(new HealingReport.Entry(locator.description(), locator.by().toString(),
                            candidate.toString(), healer.name(), url, Instant.now()));
                    String message = "Healed " + locator + " -> " + candidate + " using " + healer.name()
                            + ". Update the locator in code.";
                    LOG.warn(message);
                    Reporter.attachText("Self-healed locator", message);
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    static boolean matchesExactlyOneDisplayed(WebDriver driver, By candidate) {
        try {
            List<WebElement> found = driver.findElements(candidate);
            return found.size() == 1 && found.get(0).isDisplayed();
        } catch (RuntimeException e) {
            return false; // invalid selector proposed by a healer
        }
    }

    private static List<LocatorHealer> healers() {
        List<LocatorHealer> result = healers;
        if (result == null) {
            List<LocatorHealer> all = new ArrayList<>(ServiceLoaders.load(LocatorHealer.class));
            if (all.stream().noneMatch(HeuristicLocatorHealer.class::isInstance)) {
                all.add(new HeuristicLocatorHealer());
            }
            all.sort(Comparator.comparingInt(LocatorHealer::priority).reversed());
            result = List.copyOf(all);
            healers = result;
        }
        return result;
    }

    /** Replaces the discovered healers; for tests and custom setups. */
    public static void useHealers(List<LocatorHealer> custom) {
        healers = List.copyOf(custom);
        CACHE.clear();
    }

    private static String safe(java.util.function.Supplier<String> supplier) {
        try {
            String value = supplier.get();
            return value == null ? "" : value;
        } catch (RuntimeException e) {
            return "";
        }
    }
}
