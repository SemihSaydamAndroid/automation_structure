package io.github.semihsaydamandroid.automation.api.karate;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.qameta.allure.karate.AllureKarate;

/**
 * Parallel Karate runner bound to the shared configuration:
 *
 * <pre>{@code
 * @Test
 * void users() {
 *     ApiSuite.features("classpath:features/users").tags("@smoke").run().assertPassed();
 * }
 * }</pre>
 *
 * {@code karate.env} follows {@code automation.env} unless set explicitly; threads, tags and the
 * report folder come from {@code api.*} configuration and can be overridden fluently.
 */
public final class ApiSuite {

    private static final Logger LOG = LoggerFactory.getLogger(ApiSuite.class);

    private final List<String> paths;
    private final List<String> tags = new ArrayList<>();
    private int threads;
    private String reportDir;

    private ApiSuite(List<String> paths) {
        AutomationConfig config = AutomationConfig.get();
        this.paths = paths;
        this.threads = config.getInt("api.threads", 4);
        this.reportDir = config.get("api.report-dir", "target/karate-reports");
        this.tags.addAll(config.getList("api.tags"));
    }

    public static ApiSuite features(String... paths) {
        return new ApiSuite(List.of(paths));
    }

    /** Karate tag expressions, e.g. {@code "@smoke"}, {@code "~@wip"}, {@code "@users,@orders"}. */
    public ApiSuite tags(String... expressions) {
        tags.addAll(List.of(expressions));
        return this;
    }

    public ApiSuite threads(int count) {
        this.threads = count;
        return this;
    }

    public ApiSuite reportDir(String dir) {
        this.reportDir = dir;
        return this;
    }

    public Result run() {
        AutomationConfig config = AutomationConfig.get();
        String karateEnv = System.getProperty("karate.env", config.context().env());
        LOG.info("Running Karate {} (tags={}, env={}, threads={})", paths, tags, karateEnv, threads);
        Runner.Builder<?> builder = Runner.path(paths)
                .karateEnv(karateEnv)
                .reportDir(reportDir)
                .outputCucumberJson(true)
                .outputJunitXml(true)
                .hook(new ReportingHook())
                .hook(new AllureKarate());
        if (!tags.isEmpty()) {
            builder.tags(tags);
        }
        return new Result(builder.parallel(threads));
    }

    /** Karate results with an assertion that lists every failure. */
    public record Result(Results results) {

        public Result assertPassed() {
            if (results.getFailCount() > 0) {
                throw new AssertionError(results.getFailCount() + " Karate scenario(s) failed:\n"
                        + results.getErrorMessages() + "\nReport: " + results.getReportDir());
            }
            if (results.getScenariosPassed() == 0) {
                throw new AssertionError("No Karate scenarios were executed; check paths and tags");
            }
            return this;
        }

        public int passed() {
            return results.getScenariosPassed();
        }

        public int failed() {
            return results.getScenariosFailed();
        }
    }
}
