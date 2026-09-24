package io.github.semihsaydamandroid.automation.core.spi;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import io.github.semihsaydamandroid.automation.core.spi.FailureAnalysis.Category;

/**
 * Deterministic, offline triage based on well-known failure signatures. Always available and used
 * as the fallback when no AI analyzer is configured or the model is unreachable.
 */
public final class RuleBasedFailureAnalyzer implements FailureAnalyzer {

    private record Rule(Pattern pattern, Category category, double confidence, String rootCause, String fix) {
    }

    private static final List<Rule> RULES = List.of(
            rule("UnknownHostException|Connection refused|ConnectException|No route to host|"
                            + "ERR_NAME_NOT_RESOLVED|ERR_CONNECTION_REFUSED",
                    Category.ENVIRONMENT, 0.9,
                    "The system under test or a dependency is unreachable.",
                    "Check that the target environment is up and reachable from where the test ran "
                            + "(pod network, VPN, DNS)."),
            rule("status code (502|503|504)|HTTP/1\\.1 (502|503|504)|responseStatus == (502|503|504)|"
                            + "Service Unavailable|Bad Gateway|Gateway Timeout",
                    Category.ENVIRONMENT, 0.8,
                    "The service answered with a gateway/availability error.",
                    "Check deployment health and upstream dependencies; retry after the environment recovers."),
            rule("status code (401|403)|responseStatus == (401|403)|Unauthorized|Forbidden",
                    Category.ENVIRONMENT, 0.6,
                    "Authentication or authorization was rejected.",
                    "Verify credentials/secrets for this environment and the token scopes."),
            rule("StaleElementReferenceException",
                    Category.FLAKY, 0.7,
                    "The page re-rendered while the test held a reference to an element.",
                    "Re-locate the element after the page updates; use UiElement which re-resolves on every action."),
            rule("NoSuchElementException|Unable to locate element|waiting for (visibility|presence|element)",
                    Category.LOCATOR_CHANGED, 0.6,
                    "An expected element was not found; the locator may be outdated or the page did not load.",
                    "Compare the locator with the current DOM (see page source attachment) or enable self-healing."),
            rule("ElementClickInterceptedException|element click intercepted",
                    Category.TEST_BUG, 0.6,
                    "Another element (overlay, cookie banner, spinner) covered the click target.",
                    "Wait for overlays to disappear or dismiss them before clicking."),
            rule("TimeoutException|timed out|SocketTimeoutException",
                    Category.ENVIRONMENT, 0.4,
                    "An operation exceeded its timeout.",
                    "Check environment performance; if it is expected to be slow, raise the specific wait, not global timeouts."),
            rule("SessionNotCreatedException|session not created|DevToolsActivePort",
                    Category.ENVIRONMENT, 0.8,
                    "The browser session could not be created.",
                    "Check browser/driver versions, Grid or pod capacity, and /dev/shm size for containers."),
            rule("JsonParseException|MismatchedInputException|invalid json",
                    Category.PRODUCT_BUG, 0.5,
                    "The response body did not match the expected format.",
                    "Inspect the response payload; the API contract may have changed."),
            rule("AssertionError|AssertionFailedError|expected:|did not match|match failed",
                    Category.PRODUCT_BUG, 0.4,
                    "An assertion on the system's behavior failed.",
                    "Compare expected vs. actual values; if the behavior change is intended, update the test."));

    private static Rule rule(String regex, Category category, double confidence, String rootCause, String fix) {
        return new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), category, confidence, rootCause, fix);
    }

    @Override
    public int priority() {
        return -100;
    }

    @Override
    public Optional<FailureAnalysis> analyze(FailureContext context) {
        String evidence = context.errorSummary(40) + "\n" + (context.message() == null ? "" : context.message());
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(evidence).find()) {
                return Optional.of(new FailureAnalysis(rule.category(), rule.confidence(),
                        rule.category() + " suspected in " + context.testName(),
                        rule.rootCause(), rule.fix(), "rules"));
            }
        }
        return Optional.of(new FailureAnalysis(Category.UNKNOWN, 0.1,
                "No known failure signature matched for " + context.testName(),
                "Unknown", "Inspect attachments; enable the AI analyzer (ai.provider) for deeper triage.", "rules"));
    }
}
