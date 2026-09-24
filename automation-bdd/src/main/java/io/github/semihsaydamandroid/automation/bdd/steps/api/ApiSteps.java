package io.github.semihsaydamandroid.automation.bdd.steps.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.intuit.karate.Runner;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.semihsaydamandroid.automation.bdd.support.ScenarioContext;

/**
 * Bridges Cucumber scenarios to Karate: prepare or verify state through the API inside a UI
 * scenario ("create the customer via API, then log in through the browser").
 * Add {@code io.github.semihsaydamandroid.automation.bdd.steps.api} to the glue to use it.
 *
 * <pre>
 * When I call API feature "classpath:api/create-user.feature" with:
 *   | name | #{Name.firstName} |
 * Then the API result "userId" should not be empty
 * And I type "${userId}" into "User id"
 * </pre>
 *
 * Every top-level variable defined by the called feature becomes a scenario variable.
 */
public class ApiSteps {

    private static final String WRAPPER = """
            Feature: Cucumber to Karate bridge

              Scenario: call
                * def bridgeResult = karate.call(bridgeTarget, bridgeArgs)
            """;
    private static Path wrapper;

    private final ScenarioContext context;

    public ApiSteps(ScenarioContext context) {
        this.context = context;
    }

    @When("I call API feature {string}")
    @When("{string} API senaryosunu çağırırım")
    public void call(String feature) {
        call(feature, Map.of());
    }

    @When("I call API feature {string} with:")
    @When("{string} API senaryosunu aşağıdaki değerlerle çağırırım:")
    public void callWith(String feature, DataTable arguments) {
        call(feature, arguments.asMap(String.class, String.class));
    }

    @Then("the API result {string} should be {string}")
    @Then("API sonucu {string} değeri {string} olmalı")
    public void resultShouldBe(String variable, String expected) {
        String actual = String.valueOf(context.get(variable));
        String resolved = context.resolve(expected);
        if (!resolved.equals(actual)) {
            throw new AssertionError("API result '" + variable + "' was '" + actual + "', expected '" + resolved + "'");
        }
    }

    @Then("the API result {string} should not be empty")
    @Then("API sonucu {string} boş olmamalı")
    public void resultShouldNotBeEmpty(String variable) {
        Object value = context.get(variable);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new AssertionError("API result '" + variable + "' is empty; variables: " + context.variables().keySet());
        }
    }

    /**
     * Runs the target through {@code karate.call} from a one-line wrapper feature. Called features
     * behave exactly as in Karate: karate-config.js applies and {@code @ignore} (the usual tag on
     * reusable features) does not prevent the call.
     */
    @SuppressWarnings("unchecked")
    private void call(String feature, Map<String, String> arguments) {
        Map<String, Object> args = new LinkedHashMap<>();
        arguments.forEach((k, v) -> args.put(k, context.resolve(v)));
        Map<String, Object> vars = new HashMap<>();
        vars.put("bridgeTarget", context.resolve(feature));
        vars.put("bridgeArgs", args);
        Map<String, Object> result = Runner.runFeature(wrapper().toFile(), vars, true);
        Object called = result.get("bridgeResult");
        if (called instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).forEach(context::set);
        }
    }

    private static synchronized Path wrapper() {
        if (wrapper == null || !Files.exists(wrapper)) {
            try {
                wrapper = Files.createTempFile("automation-cucumber-bridge-", ".feature");
                Files.writeString(wrapper, WRAPPER);
                wrapper.toFile().deleteOnExit();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create the Karate bridge feature", e);
            }
        }
        return wrapper;
    }
}
