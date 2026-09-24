package io.github.semihsaydamandroid.automation.core.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.semihsaydamandroid.automation.core.spi.FailureAnalysis.Category;

class RuleBasedFailureAnalyzerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "status code was: 503, expected: 200                              | ENVIRONMENT",
        "status code was: 401, expected: 200                              | ENVIRONMENT",
        "java.net.UnknownHostException: api.test.local                    | ENVIRONMENT",
        "org.openqa.selenium.NoSuchElementException: Unable to locate element | LOCATOR_CHANGED",
        "org.openqa.selenium.StaleElementReferenceException: stale        | FLAKY",
        "element click intercepted: other element would receive the click | TEST_BUG",
        "match failed: EQUALS $.name expected 'Ada' but was 'Bob'          | PRODUCT_BUG",
        "something nobody has seen before                                  | UNKNOWN"
    })
    void classifiesKnownSignatures(String message, Category expected) {
        FailureContext context = new FailureContext("t", "api", null, message, Map.of(), null);

        assertThat(new RuleBasedFailureAnalyzer().analyze(context))
                .hasValueSatisfying(a -> assertThat(a.category()).isEqualTo(expected));
    }
}
