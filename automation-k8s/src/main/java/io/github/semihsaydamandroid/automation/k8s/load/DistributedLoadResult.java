package io.github.semihsaydamandroid.automation.k8s.load;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.semihsaydamandroid.automation.perf.Sla;
import io.github.semihsaydamandroid.automation.perf.stats.Metrics;
import io.github.semihsaydamandroid.automation.perf.stats.SlaCheck;

/**
 * Merged outcome of all load generators.
 *
 * @param workerProblems workers that failed, were evicted or whose JMeter exited abnormally
 * @param resultsDir     local folder with every worker's JTL and jmeter.log
 */
public record DistributedLoadResult(
        String name,
        int workers,
        int threadsPerWorker,
        Metrics overall,
        List<Metrics> byLabel,
        Duration duration,
        List<String> workerProblems,
        Sla sla,
        Path resultsDir) {

    public List<String> violations() {
        List<String> violations = new ArrayList<>(workerProblems);
        violations.addAll(SlaCheck.violations(overall, sla));
        return violations;
    }

    public DistributedLoadResult assertSla() {
        List<String> violations = violations();
        if (!violations.isEmpty()) {
            throw new AssertionError("Distributed load test '" + name + "' failed: " + String.join("; ", violations)
                    + "\n" + summary());
        }
        return this;
    }

    public String summary() {
        return "workers=%d threads/worker=%d total-threads=%d duration=%ss%n"
                .formatted(workers, threadsPerWorker, workers * threadsPerWorker, duration.toSeconds())
                + SlaCheck.table(byLabel, overall)
                + "results: " + resultsDir.toAbsolutePath() + "\n";
    }
}
