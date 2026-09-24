package io.github.semihsaydamandroid.automation.perf;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.stats.StatsSummary;

/** Statistics of a run plus SLA evaluation. */
public record PerfResult(String name, TestPlanStats stats, Sla sla, Path reportDir) {

    public StatsSummary overall() {
        return stats.overall();
    }

    public double errorRatePercent() {
        long samples = overall().samplesCount();
        return samples == 0 ? 0 : overall().errorsCount() * 100.0 / samples;
    }

    public double throughput() {
        double seconds = Math.max(stats.duration().toMillis(), 1) / 1000.0;
        return overall().samplesCount() / seconds;
    }

    public List<String> violations() {
        List<String> violations = new ArrayList<>();
        StatsSummary overall = overall();
        if (overall.samplesCount() == 0) {
            violations.add("no samples were recorded");
            return violations;
        }
        check(violations, "p95", overall.sampleTime().perc95(), sla.p95());
        check(violations, "p99", overall.sampleTime().perc99(), sla.p99());
        if (sla.maxErrorRatePercent() != null && errorRatePercent() > sla.maxErrorRatePercent()) {
            violations.add("error rate %.2f%% > %.2f%%".formatted(errorRatePercent(), sla.maxErrorRatePercent()));
        }
        if (sla.minThroughput() != null && throughput() < sla.minThroughput()) {
            violations.add("throughput %.1f/s < %.1f/s".formatted(throughput(), sla.minThroughput()));
        }
        return violations;
    }

    /** Throws an AssertionError listing every violated objective. */
    public PerfResult assertSla() {
        List<String> violations = violations();
        if (!violations.isEmpty()) {
            throw new AssertionError("Performance SLA violated for '" + name + "': " + String.join("; ", violations)
                    + "\n" + summary());
        }
        return this;
    }

    public String summary() {
        StatsSummary o = overall();
        StringBuilder out = new StringBuilder();
        out.append("%-30s %8s %8s %8s %8s %8s %8s%n".formatted("label", "samples", "errors", "mean", "p90", "p95", "p99"));
        for (String label : stats.labels()) {
            out.append(row(label, stats.byLabel(label)));
        }
        out.append(row("TOTAL", o));
        out.append("duration=%ss throughput=%.1f/s error-rate=%.2f%%%n"
                .formatted(stats.duration().toSeconds(), throughput(), errorRatePercent()));
        if (reportDir != null) {
            out.append("report: ").append(reportDir.toAbsolutePath()).append('\n');
        }
        return out.toString();
    }

    private static String row(String label, StatsSummary s) {
        return "%-30s %8d %8d %8d %8d %8d %8d%n".formatted(
                label.length() > 30 ? label.substring(0, 30) : label,
                s.samplesCount(), s.errorsCount(),
                s.sampleTime().mean().toMillis(), s.sampleTime().perc90().toMillis(),
                s.sampleTime().perc95().toMillis(), s.sampleTime().perc99().toMillis());
    }

    private static void check(List<String> violations, String metric, Duration actual, Duration limit) {
        if (limit != null && actual.compareTo(limit) > 0) {
            violations.add(metric + " " + actual.toMillis() + "ms > " + limit.toMillis() + "ms");
        }
    }
}
