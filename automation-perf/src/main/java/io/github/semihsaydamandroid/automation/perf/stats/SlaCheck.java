package io.github.semihsaydamandroid.automation.perf.stats;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.github.semihsaydamandroid.automation.perf.Sla;

/** Evaluates an {@link Sla} against metrics and renders summaries. */
public final class SlaCheck {

    private SlaCheck() {
    }

    public static List<String> violations(Metrics overall, Sla sla) {
        List<String> violations = new ArrayList<>();
        if (overall.samples() == 0) {
            violations.add("no samples were recorded");
            return violations;
        }
        check(violations, "p95", overall.p95(), sla.p95());
        check(violations, "p99", overall.p99(), sla.p99());
        if (sla.maxErrorRatePercent() != null && overall.errorRatePercent() > sla.maxErrorRatePercent()) {
            violations.add("error rate %.2f%% > %.2f%%".formatted(overall.errorRatePercent(), sla.maxErrorRatePercent()));
        }
        if (sla.minThroughput() != null && overall.throughput() < sla.minThroughput()) {
            violations.add("throughput %.1f/s < %.1f/s".formatted(overall.throughput(), sla.minThroughput()));
        }
        return violations;
    }

    public static String table(Collection<Metrics> byLabel, Metrics overall) {
        StringBuilder out = new StringBuilder();
        out.append("%-30s %9s %8s %8s %8s %8s %8s %10s%n"
                .formatted("label", "samples", "errors", "mean", "p90", "p95", "p99", "rps"));
        byLabel.forEach(m -> out.append(row(m)));
        out.append(row(overall));
        out.append("error-rate=%.2f%%%n".formatted(overall.errorRatePercent()));
        return out.toString();
    }

    private static String row(Metrics m) {
        String label = m.label().length() > 30 ? m.label().substring(0, 30) : m.label();
        return "%-30s %9d %8d %8d %8d %8d %8d %10.1f%n".formatted(label, m.samples(), m.errors(),
                m.mean().toMillis(), m.p90().toMillis(), m.p95().toMillis(), m.p99().toMillis(), m.throughput());
    }

    private static void check(List<String> violations, String metric, Duration actual, Duration limit) {
        if (limit != null && actual.compareTo(limit) > 0) {
            violations.add(metric + " " + actual.toMillis() + "ms > " + limit.toMillis() + "ms");
        }
    }
}
