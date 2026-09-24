package io.github.semihsaydamandroid.automation.perf;

import static us.abstracta.jmeter.javadsl.JmeterDsl.htmlReporter;
import static us.abstracta.jmeter.javadsl.JmeterDsl.influxDbListener;
import static us.abstracta.jmeter.javadsl.JmeterDsl.jtlWriter;
import static us.abstracta.jmeter.javadsl.JmeterDsl.testPlan;
import static us.abstracta.jmeter.javadsl.JmeterDsl.threadGroup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.report.Reporter;
import io.github.semihsaydamandroid.automation.core.util.Names;
import us.abstracta.jmeter.javadsl.core.DslJmeterEngine;
import us.abstracta.jmeter.javadsl.core.DslTestPlan;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import us.abstracta.jmeter.javadsl.core.engines.DistributedJmeterEngine;
import us.abstracta.jmeter.javadsl.core.engines.EmbeddedJmeterEngine;
import us.abstracta.jmeter.javadsl.core.listeners.InfluxDbBackendListener;
import us.abstracta.jmeter.javadsl.core.threadgroups.BaseThreadGroup.ThreadGroupChild;
import us.abstracta.jmeter.javadsl.core.threadgroups.DslDefaultThreadGroup;

/**
 * JMeter test as code, sized by a {@link LoadProfile} and judged by an {@link Sla}:
 *
 * <pre>{@code
 * PerfTest.named("users-api")
 *     .scenario(httpSampler("get user", baseUrl + "/users/1"))
 *     .sla(Sla.none().p95Below(Duration.ofMillis(300)).errorRateBelow(1))
 *     .run()
 *     .assertSla();
 * }</pre>
 *
 * Profile, SLA, engine and report settings default to {@code perf.*} configuration, so the same
 * test runs as a 1-user smoke check locally and as a full load test in the pipeline
 * ({@code -Dperf.profile=load}).
 */
public final class PerfTest {

    private static final Logger LOG = LoggerFactory.getLogger(PerfTest.class);

    private final String name;
    private final AutomationConfig config;
    private final List<ThreadGroupChild> children = new ArrayList<>();
    private LoadProfile profile;
    private Sla sla;

    private PerfTest(String name, AutomationConfig config) {
        this.name = name;
        this.config = config;
        this.profile = LoadProfile.current(config);
        this.sla = Sla.fromConfig(config);
    }

    public static PerfTest named(String name) {
        return new PerfTest(name, AutomationConfig.get());
    }

    /** Base URL for samplers: {@code perf.base-url}, falling back to {@code api.base-url}. */
    public static String baseUrl() {
        AutomationConfig config = AutomationConfig.get();
        return config.find("perf.base-url").filter(s -> !s.isBlank()).orElseGet(() -> config.get("api.base-url"));
    }

    public PerfTest scenario(ThreadGroupChild... steps) {
        children.addAll(List.of(steps));
        return this;
    }

    public PerfTest profile(String profileName) {
        this.profile = LoadProfile.named(config, profileName);
        return this;
    }

    public PerfTest profile(LoadProfile loadProfile) {
        this.profile = loadProfile;
        return this;
    }

    public PerfTest sla(Sla objectives) {
        this.sla = objectives;
        return this;
    }

    public PerfResult run() {
        guardLocalLoad();
        Path reportDir = Path.of(config.get("perf.report-dir", "target/jmeter"),
                Names.toDnsLabel(name) + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        List<DslTestPlan.TestPlanChild> elements = new ArrayList<>(planChildren(profile));
        elements.add(jtlWriter(reportDir.resolve("jtl").toString()));
        if (config.getBoolean("perf.report.html", true)) {
            elements.add(htmlReporter(reportDir.resolve("html").toString()));
        }
        DslTestPlan plan = testPlan(elements.toArray(DslTestPlan.TestPlanChild[]::new));

        LOG.info("Running performance test '{}' with profile {} on {}", name, profile, engineDescription());
        TestPlanStats stats = execute(plan);
        PerfResult result = new PerfResult(name, stats, sla, reportDir);
        String summary = result.summary();
        LOG.info("Performance summary for '{}':\n{}", name, summary);
        Reporter.attachText("Performance summary - " + name, summary);
        return result;
    }

    private void guardLocalLoad() {
        ExecutionContext context = config.context();
        int limit = config.getInt("perf.guard.local-max-threads", 5);
        if (context.isLocal() && !context.insideKubernetes() && profile.threads() > limit
                && !config.getBoolean("perf.guard.allow-local-load", false)) {
            throw new IllegalStateException("Refusing to start " + profile.threads() + " threads (profile '"
                    + profile.name() + "') from a local machine; limit is perf.guard.local-max-threads=" + limit
                    + ". Run it from the pipeline or the Kubernetes remote runner, "
                    + "or set perf.guard.allow-local-load=true deliberately.");
        }
    }

    /** Saves the generated plan as .jmx, e.g. to open it in the JMeter GUI. */
    public void saveAsJmx(Path file) {
        saveAsJmx(file, profile);
    }

    /**
     * Saves the plan sized for {@code loadProfile}; used by distributed runs where every load
     * generator receives its share of the total threads.
     */
    public void saveAsJmx(Path file, LoadProfile loadProfile) {
        try {
            testPlan(planChildren(loadProfile).toArray(DslTestPlan.TestPlanChild[]::new)).saveAsJmx(file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String name() {
        return name;
    }

    public LoadProfile loadProfile() {
        return profile;
    }

    public Sla slaObjectives() {
        return sla;
    }

    /** Thread group sized by the profile plus the live metrics listener when configured. */
    private List<DslTestPlan.TestPlanChild> planChildren(LoadProfile loadProfile) {
        ThreadGroupChild[] steps = children.toArray(ThreadGroupChild[]::new);
        DslDefaultThreadGroup group = loadProfile.iterationBased()
                ? threadGroup(name, loadProfile.threads(), loadProfile.iterations(), steps)
                : threadGroup(name).rampToAndHold(loadProfile.threads(), loadProfile.rampUp(), loadProfile.hold()).children(steps);
        List<DslTestPlan.TestPlanChild> result = new ArrayList<>();
        result.add(group);
        config.find("perf.live.influxdb-url").filter(s -> !s.isBlank()).ifPresent(url -> {
            InfluxDbBackendListener listener = influxDbListener(url)
                    .application(name)
                    .tag("runId", config.context().runId())
                    .tag("profile", config.context().profile().id());
            config.find("perf.live.influxdb-token").filter(s -> !s.isBlank()).ifPresent(listener::token);
            result.add(listener);
        });
        return result;
    }

    private TestPlanStats execute(DslTestPlan plan) {
        try {
            return plan.runIn(engine(config));
        } catch (IOException e) {
            throw new UncheckedIOException("Performance test '" + name + "' failed to run", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Performance test '" + name + "' interrupted", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Performance test '" + name + "' timed out", e);
        }
    }

    /** {@code perf.engine=embedded} (default) or {@code distributed} with {@code perf.distributed.hosts}. */
    static DslJmeterEngine engine(AutomationConfig config) {
        String engine = config.get("perf.engine", "embedded");
        EmbeddedJmeterEngine result = switch (engine) {
            case "embedded" -> new EmbeddedJmeterEngine();
            case "distributed" -> {
                List<String> hosts = config.getList("perf.distributed.hosts");
                if (hosts.isEmpty()) {
                    throw new IllegalStateException("perf.engine=distributed requires perf.distributed.hosts");
                }
                yield new DistributedJmeterEngine(hosts.toArray(String[]::new)).stopEnginesOnTestEnd();
            }
            default -> throw new IllegalStateException("Unknown perf.engine '" + engine + "' (embedded | distributed)");
        };
        config.section("perf.jmeter-props").forEach(result::prop);
        return result;
    }

    private String engineDescription() {
        return config.get("perf.engine", "embedded");
    }
}
