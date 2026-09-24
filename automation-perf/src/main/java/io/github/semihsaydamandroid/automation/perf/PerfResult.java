package io.github.semihsaydamandroid.automation.perf;

import java.nio.file.Path;
import java.util.List;

import io.github.semihsaydamandroid.automation.perf.stats.Metrics;
import io.github.semihsaydamandroid.automation.perf.stats.SlaCheck;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.stats.StatsSummary;

/** Statistics of an embedded or JMX run plus SLA evaluation. */
public record PerfResult(String name, TestPlanStats stats, Sla sla, Path reportDir) {

    public StatsSummary overall() {
        return stats.overall();
    }

    public Metrics metrics() {
        return Metrics.of("TOTAL", stats.overall(), stats.duration());
    }

    public List<Metrics> metricsByLabel() {
        return stats.labels().stream().sorted().map(l -> Metrics.of(l, stats.byLabel(l), stats.duration())).toList();
    }

    public double errorRatePercent() {
        return metrics().errorRatePercent();
    }

    public double throughput() {
        return metrics().throughput();
    }

    public List<String> violations() {
        return SlaCheck.violations(metrics(), sla);
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
        String table = SlaCheck.table(metricsByLabel(), metrics());
        return table + "duration=" + stats.duration().toSeconds() + "s"
                + (reportDir == null ? "" : "\nreport: " + reportDir.toAbsolutePath()) + "\n";
    }
}
