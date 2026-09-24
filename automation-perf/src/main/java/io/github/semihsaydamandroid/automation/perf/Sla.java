package io.github.semihsaydamandroid.automation.perf;

import java.time.Duration;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/**
 * Service level objectives checked after a run. Unset limits are not checked.
 *
 * @param p95                 maximum 95th percentile response time
 * @param p99                 maximum 99th percentile response time
 * @param maxErrorRatePercent maximum share of failed samples, 0..100
 * @param minThroughput       minimum overall samples per second
 */
public record Sla(Duration p95, Duration p99, Double maxErrorRatePercent, Double minThroughput) {

    public static Sla none() {
        return new Sla(null, null, null, null);
    }

    /** From {@code perf.sla.p95}, {@code perf.sla.p99}, {@code perf.sla.error-rate}, {@code perf.sla.throughput}. */
    public static Sla fromConfig(AutomationConfig config) {
        return new Sla(
                config.find("perf.sla.p95").filter(s -> !s.isBlank()).map(v -> config.getDuration("perf.sla.p95", null)).orElse(null),
                config.find("perf.sla.p99").filter(s -> !s.isBlank()).map(v -> config.getDuration("perf.sla.p99", null)).orElse(null),
                config.find("perf.sla.error-rate").filter(s -> !s.isBlank()).map(Double::parseDouble).orElse(null),
                config.find("perf.sla.throughput").filter(s -> !s.isBlank()).map(Double::parseDouble).orElse(null));
    }

    public Sla p95Below(Duration limit) {
        return new Sla(limit, p99, maxErrorRatePercent, minThroughput);
    }

    public Sla p99Below(Duration limit) {
        return new Sla(p95, limit, maxErrorRatePercent, minThroughput);
    }

    public Sla errorRateBelow(double percent) {
        return new Sla(p95, p99, percent, minThroughput);
    }

    public Sla throughputAbove(double samplesPerSecond) {
        return new Sla(p95, p99, maxErrorRatePercent, samplesPerSecond);
    }
}
