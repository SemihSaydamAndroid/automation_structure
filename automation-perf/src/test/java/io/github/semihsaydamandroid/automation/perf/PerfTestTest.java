package io.github.semihsaydamandroid.automation.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpSampler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;

class PerfTestTest {

    private static HttpServer server;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @AfterEach
    void resetConfig() {
        AutomationConfig.reset();
    }

    private static void configure(Map<String, String> values) {
        AutomationConfig.set(AutomationConfig.load().with(values));
    }

    @Test
    void runsProfileFromConfigurationAndMeetsSla() {
        configure(Map.of("perf.base-url", baseUrl, "perf.report.html", "false",
                "perf.profile", "tiny", "perf.profile.tiny.threads", "2", "perf.profile.tiny.iterations", "5"));

        PerfResult result = PerfTest.named("health")
                .scenario(httpSampler("GET /health", PerfTest.baseUrl() + "/health"))
                .sla(Sla.none().p95Below(Duration.ofSeconds(2)).errorRateBelow(0.1))
                .run()
                .assertSla();

        assertThat(result.overall().samplesCount()).isEqualTo(10);
        assertThat(result.errorRatePercent()).isZero();
        assertThat(result.summary()).contains("GET /health", "TOTAL");
    }

    @Test
    void slaViolationsAreReported() {
        configure(Map.of("perf.report.html", "false"));

        PerfResult result = PerfTest.named("missing")
                .profile(LoadProfile.iterations(1, 3))
                .scenario(httpSampler("GET /missing", baseUrl + "/missing"))
                .sla(Sla.none().errorRateBelow(1))
                .run();

        assertThat(result.violations()).singleElement().asString().contains("error rate 100.00%");
        assertThatThrownBy(result::assertSla).isInstanceOf(AssertionError.class).hasMessageContaining("SLA violated");
    }

    @Test
    void localMachinesCannotStartRealLoadByAccident() {
        configure(Map.of("automation.profile", "local", "perf.profile", "load"));

        assertThatThrownBy(() -> PerfTest.named("oops").scenario(httpSampler(baseUrl + "/health")).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perf.guard.local-max-threads");
    }

    @Test
    void existingJmxPlansRunWithProperties(@TempDir Path dir) {
        configure(Map.of("perf.report.html", "false"));
        Path jmx = dir.resolve("legacy.jmx");
        PerfTest.named("legacy")
                .profile(LoadProfile.iterations(1, 2))
                .scenario(httpSampler("GET /health", baseUrl + "/health"))
                .saveAsJmx(jmx);

        PerfResult result = JmxPlan.run(jmx, Map.of(), Sla.none().errorRateBelow(0.1)).assertSla();

        assertThat(result.overall().samplesCount()).isEqualTo(2);
    }
}
