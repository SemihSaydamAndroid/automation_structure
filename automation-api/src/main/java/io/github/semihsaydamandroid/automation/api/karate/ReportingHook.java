package io.github.semihsaydamandroid.automation.api.karate;

import java.util.LinkedHashMap;
import java.util.Map;

import com.intuit.karate.RuntimeHook;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.StepResult;

import io.github.semihsaydamandroid.automation.core.spi.FailureAnalyzers;
import io.github.semihsaydamandroid.automation.core.spi.FailureContext;

/**
 * Runs failure analysis for failed scenarios and attaches the verdict to the Allure test case.
 * Must be registered before {@code AllureKarate} so the test case is still open.
 */
public final class ReportingHook implements RuntimeHook {

    private static final int MAX_LOG_CHARS = 20_000;

    @Override
    public void afterScenario(ScenarioRuntime runtime) {
        ScenarioResult result = runtime.result;
        if (!result.isFailed()) {
            return;
        }
        Map<String, String> artifacts = new LinkedHashMap<>();
        artifacts.put("feature", String.valueOf(runtime.scenario.getUriToLineNumber()));
        StepResult failed = result.getFailedStep();
        if (failed != null) {
            artifacts.put("failedStep", failed.getStep().getPrefix() + " " + failed.getStep().getText());
            String log = failed.getStepLog();
            if (log != null && !log.isBlank()) {
                artifacts.put("httpLog", log.length() > MAX_LOG_CHARS ? log.substring(log.length() - MAX_LOG_CHARS) : log);
            }
        }
        FailureAnalyzers.analyzeAndReport(new FailureContext(
                runtime.scenario.getRefIdAndName(), "api", result.getError(), result.getErrorMessage(), artifacts, null));
    }
}
