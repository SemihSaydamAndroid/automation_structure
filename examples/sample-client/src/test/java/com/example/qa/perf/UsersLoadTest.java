package com.example.qa.perf;

import static us.abstracta.jmeter.javadsl.JmeterDsl.httpSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.responseAssertion;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.semihsaydamandroid.automation.k8s.load.DistributedLoadTest;
import io.github.semihsaydamandroid.automation.perf.JmxPlan;
import io.github.semihsaydamandroid.automation.perf.PerfTest;
import io.github.semihsaydamandroid.automation.perf.Sla;

@Tag("perf")
class UsersLoadTest {

    static PerfTest usersScenario() {
        String base = PerfTest.baseUrl();
        return PerfTest.named("users-api")
                .scenario(
                        httpSampler("GET /users/1", base + "/users/1").children(responseAssertion().containsSubstrings("\"id\": 1")),
                        httpSampler("GET /posts", base + "/posts"));
    }

    /** Profile from perf.profile: 1 user locally (smoke), -Dperf.profile=load in the pipeline. */
    @Test
    void usersApiMeetsSla() {
        usersScenario()
                .sla(Sla.none().p95Below(Duration.ofMillis(1500)).errorRateBelow(1))
                .run()
                .assertSla();
    }

    /** Existing JMeter assets keep working. */
    @Test
    void legacyJmxPlan() {
        JmxPlan.run(Path.of("src/test/resources/perf/legacy.jmx"), java.util.Map.of("threads", 1), Sla.none().errorRateBelow(1))
                .assertSla();
    }

    /** 40 pods x 50 threads from the cluster; only allowed hosts, only from the pipeline. */
    @Test
    @Tag("k8s")
    void checkoutUnderDistributedLoad() {
        DistributedLoadTest.of(usersScenario())
                .workers(40)
                .totalThreads(2000)
                .sla(Sla.none().p95Below(Duration.ofMillis(800)).errorRateBelow(0.5))
                .run()
                .assertSla();
    }
}
