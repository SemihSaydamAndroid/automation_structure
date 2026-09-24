package io.github.semihsaydamandroid.automation.perf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.report.Reporter;
import us.abstracta.jmeter.javadsl.core.DslTestPlan;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.engines.EmbeddedJmeterEngine;

/**
 * Runs existing JMeter {@code .jmx} plans unchanged, so current JMeter assets keep working while
 * new tests are written as code. Properties are passed as JMeter properties ({@code ${__P(threads,1)}}).
 */
public final class JmxPlan {

    private static final Logger LOG = LoggerFactory.getLogger(JmxPlan.class);

    private JmxPlan() {
    }

    public static PerfResult run(Path jmx, Map<String, Object> properties, Sla sla) {
        if (!Files.isRegularFile(jmx)) {
            throw new IllegalArgumentException("JMX file not found: " + jmx.toAbsolutePath());
        }
        try {
            EmbeddedJmeterEngine engine = (EmbeddedJmeterEngine) PerfTest.engine(AutomationConfig.get());
            properties.forEach(engine::prop);
            LOG.info("Running JMX plan {} with properties {}", jmx, properties.keySet());
            TestPlanStats stats = DslTestPlan.fromJmx(jmx.toString()).runIn(engine);
            PerfResult result = new PerfResult(jmx.getFileName().toString(), stats, sla, null);
            Reporter.attachText("Performance summary - " + jmx.getFileName(), result.summary());
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot run " + jmx, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + jmx, e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out running " + jmx, e);
        }
    }
}
