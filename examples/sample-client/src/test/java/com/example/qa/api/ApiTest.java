package com.example.qa.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.semihsaydamandroid.automation.api.karate.ApiSuite;

@Tag("api")
class ApiTest {

    @Test
    void users() {
        ApiSuite.features("classpath:features/api").run().assertPassed();
    }

    @Test
    void smoke() {
        ApiSuite.features("classpath:features/api").tags("@smoke").run().assertPassed();
    }
}
