package io.github.semihsaydamandroid.automation.ui.healing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.data.TestData;

/**
 * Collects every healed locator and writes them to {@code ui.healing.report}
 * (default {@code target/healing-report.json}) when the JVM exits. CI can publish this file or
 * fail the build when it is not empty, so healed locators are fixed in code quickly.
 */
public final class HealingReport {

    private static final Logger LOG = LoggerFactory.getLogger(HealingReport.class);
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(HealingReport::write, "automation-healing-report"));
    }

    public record Entry(String description, String original, String healed, String healer, String url, Instant at) {
    }

    private HealingReport() {
    }

    static void add(Entry entry) {
        ENTRIES.add(entry);
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    static void write() {
        if (ENTRIES.isEmpty()) {
            return;
        }
        Path file = Path.of(AutomationConfig.get().get("ui.healing.report", "target/healing-report.json"));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            TestData.mapper().copy()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(file.toFile(), ENTRIES);
            LOG.warn("{} locator(s) were healed during this run; see {}", ENTRIES.size(), file.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not write healing report {}: {}", file, e.toString());
        }
    }
}
