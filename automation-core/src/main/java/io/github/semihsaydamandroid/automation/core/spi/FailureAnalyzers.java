package io.github.semihsaydamandroid.automation.core.spi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.report.Reporter;
import io.github.semihsaydamandroid.automation.core.util.ServiceLoaders;

/** Runs the registered {@link FailureAnalyzer}s and reports the first verdict. */
public final class FailureAnalyzers {

    private static final Logger LOG = LoggerFactory.getLogger(FailureAnalyzers.class);

    private final List<FailureAnalyzer> analyzers;

    public FailureAnalyzers(List<FailureAnalyzer> analyzers) {
        this.analyzers = analyzers.stream()
                .sorted(Comparator.comparingInt(FailureAnalyzer::priority).reversed())
                .toList();
    }

    /** Rule-based analyzer plus every analyzer found on the classpath. */
    public static FailureAnalyzers discover() {
        List<FailureAnalyzer> all = new ArrayList<>(ServiceLoaders.load(FailureAnalyzer.class));
        if (all.stream().noneMatch(RuleBasedFailureAnalyzer.class::isInstance)) {
            all.add(new RuleBasedFailureAnalyzer());
        }
        return new FailureAnalyzers(all);
    }

    public Optional<FailureAnalysis> analyze(FailureContext context) {
        for (FailureAnalyzer analyzer : analyzers) {
            try {
                Optional<FailureAnalysis> result = analyzer.analyze(context);
                if (result.isPresent()) {
                    return result;
                }
            } catch (RuntimeException e) {
                LOG.warn("Failure analyzer {} failed: {}", analyzer.getClass().getSimpleName(), e.toString());
            }
        }
        return Optional.empty();
    }

    /** Analyzes and attaches the verdict to the report; never throws. */
    public static Optional<FailureAnalysis> analyzeAndReport(FailureContext context) {
        if (!AutomationConfig.get().getBoolean("report.failure-analysis.enabled", true)) {
            return Optional.empty();
        }
        try {
            Optional<FailureAnalysis> analysis = discover().analyze(context);
            analysis.ifPresent(a -> {
                LOG.info("Failure analysis for '{}': {} - {}", context.testName(), a.category(), a.summary());
                Reporter.attachMarkdown("Failure analysis", a.toMarkdown());
                Reporter.label("failureCategory", a.category().name());
            });
            return analysis;
        } catch (RuntimeException e) {
            LOG.warn("Failure analysis skipped: {}", e.toString());
            return Optional.empty();
        }
    }
}
