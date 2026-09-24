package io.github.semihsaydamandroid.automation.core.spi;

import java.util.Optional;

/**
 * SPI for classifying test failures. The UI JUnit extension and the Karate hook call every
 * registered analyzer (highest priority first) and attach the first verdict to the report.
 * <p>
 * The core module ships a deterministic {@link RuleBasedFailureAnalyzer}; the AI module adds an
 * LLM-backed one. Register implementations in
 * {@code META-INF/services/io.github.semihsaydamandroid.automation.core.spi.FailureAnalyzer}.
 */
public interface FailureAnalyzer {

    /** Higher values run first. */
    default int priority() {
        return 0;
    }

    Optional<FailureAnalysis> analyze(FailureContext context);
}
