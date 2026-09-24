package io.github.semihsaydamandroid.automation.perf.stats;

import java.time.Duration;

import us.abstracta.jmeter.javadsl.core.stats.StatsSummary;

/**
 * Response time and volume figures for one label (or {@code TOTAL}); the common currency of
 * embedded runs, JMX runs and distributed Kubernetes runs.
 */
public record Metrics(
        String label,
        long samples,
        long errors,
        Duration mean,
        Duration p90,
        Duration p95,
        Duration p99,
        Duration max,
        double throughput) {

    public static Metrics of(String label, StatsSummary stats, Duration testDuration) {
        double seconds = Math.max(testDuration.toMillis(), 1) / 1000.0;
        return new Metrics(label, stats.samplesCount(), stats.errorsCount(),
                stats.sampleTime().mean(), stats.sampleTime().perc90(), stats.sampleTime().perc95(),
                stats.sampleTime().perc99(), stats.sampleTime().max(), stats.samplesCount() / seconds);
    }

    public double errorRatePercent() {
        return samples == 0 ? 0 : errors * 100.0 / samples;
    }
}
