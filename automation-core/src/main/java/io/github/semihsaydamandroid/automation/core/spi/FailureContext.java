package io.github.semihsaydamandroid.automation.core.spi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything known about a failed test, collected by the UI/API/performance layers and handed to
 * {@link FailureAnalyzer}s.
 *
 * @param testName   display name of the failed test or scenario
 * @param layer      {@code ui}, {@code api} or {@code perf}
 * @param error      the failure; may be {@code null} when only a message is available (Karate)
 * @param message    failure message
 * @param artifacts  textual evidence such as {@code steps}, {@code pageSource}, {@code httpLog}
 * @param screenshot PNG screenshot for UI failures, otherwise {@code null}
 */
public record FailureContext(
        String testName,
        String layer,
        Throwable error,
        String message,
        Map<String, String> artifacts,
        byte[] screenshot) {

    public FailureContext {
        Map<String, String> copy = new LinkedHashMap<>();
        if (artifacts != null) {
            artifacts.forEach((k, v) -> {
                if (k != null && v != null) {
                    copy.put(k, v);
                }
            });
        }
        artifacts = Collections.unmodifiableMap(copy);
    }

    public String stackTrace() {
        if (error == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /** Message plus the first lines of the stack trace; enough for triage, cheap for an LLM. */
    public String errorSummary(int maxStackLines) {
        String trace = stackTrace();
        if (trace.isEmpty()) {
            return message == null ? "" : message;
        }
        return trace.lines().limit(maxStackLines).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
