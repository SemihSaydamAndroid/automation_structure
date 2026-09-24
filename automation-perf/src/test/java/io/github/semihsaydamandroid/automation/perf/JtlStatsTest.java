package io.github.semihsaydamandroid.automation.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.semihsaydamandroid.automation.perf.stats.JtlStats;
import io.github.semihsaydamandroid.automation.perf.stats.Metrics;
import io.github.semihsaydamandroid.automation.perf.stats.SlaCheck;

class JtlStatsTest {

    private static final String HEADER = "timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage\n";

    @Test
    void mergesWorkersAndComputesExactPercentiles(@TempDir Path dir) throws Exception {
        StringBuilder worker0 = new StringBuilder(HEADER);
        StringBuilder worker1 = new StringBuilder(HEADER);
        for (int i = 1; i <= 50; i++) {
            worker0.append(1000 + i).append(',').append(i).append(",GET /users,200,OK,t,text,true,\n");
            worker1.append(1000 + i).append(',').append(50 + i).append(",GET /users,200,OK,t,text,true,\n");
        }
        worker1.append("1100,900,\"POST /users, bulk\",500,\"Server \"\"Error\"\"\",t,text,false,\"boom, again\"\n");
        Path a = dir.resolve("worker-0.jtl");
        Files.writeString(a, worker0);
        Path b = dir.resolve("worker-1.jtl.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(b))) {
            out.write(worker1.toString().getBytes(StandardCharsets.UTF_8));
        }

        JtlStats stats = JtlStats.read(List.of(a, b));
        Metrics total = stats.overall();

        assertThat(total.samples()).isEqualTo(101);
        assertThat(total.errors()).isEqualTo(1);
        assertThat(total.p95()).isEqualTo(Duration.ofMillis(96));
        assertThat(total.max()).isEqualTo(Duration.ofMillis(900));
        assertThat(stats.byLabel()).extracting(Metrics::label).containsExactly("GET /users", "POST /users, bulk");
        assertThat(SlaCheck.violations(total, Sla.none().errorRateBelow(0.5)))
                .singleElement().asString().startsWith("error rate 0.99%");
        assertThat(SlaCheck.violations(total, Sla.none().p95Below(Duration.ofMillis(100)))).isEmpty();
    }
}
