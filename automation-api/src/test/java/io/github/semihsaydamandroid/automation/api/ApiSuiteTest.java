package io.github.semihsaydamandroid.automation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.intuit.karate.core.MockServer;

import io.github.semihsaydamandroid.automation.api.karate.ApiSuite;
import io.github.semihsaydamandroid.automation.api.karate.KarateBridge;
import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

/** Runs real Karate features against a Karate mock, so the suite is self-contained. */
class ApiSuiteTest {

    private static MockServer server;

    @BeforeAll
    static void startMock() {
        server = MockServer.feature("classpath:mocks/users-mock.feature").http(0).build();
        AutomationConfig.set(AutomationConfig.load()
                .with(Map.of("api.base-url", "http://localhost:" + server.getPort())));
    }

    @AfterAll
    static void stopMock() {
        server.stop();
        AutomationConfig.reset();
    }

    @Test
    void featuresRunAgainstTheConfiguredBaseUrl() {
        ApiSuite.Result result = ApiSuite.features("classpath:features")
                .threads(2)
                .reportDir("target/karate-reports-test")
                .run()
                .assertPassed();

        assertThat(result.passed()).isEqualTo(3);
    }

    @Test
    void tagsSelectScenarios() {
        ApiSuite.Result result = ApiSuite.features("classpath:features")
                .tags("@smoke")
                .reportDir("target/karate-reports-smoke")
                .run()
                .assertPassed();

        assertThat(result.passed()).isEqualTo(1);
    }

    @Test
    void bridgeExposesApiKeysInCamelCase() {
        Map<String, Object> config = KarateBridge.config();

        assertThat(config).containsKeys("baseUrl", "connectTimeout", "readTimeout", "env", "runId", "profile");
    }
}
