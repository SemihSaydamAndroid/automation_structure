package io.github.semihsaydamandroid.automation.bdd.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.core.data.TestData;

/**
 * State shared by all step classes of one scenario (injected by PicoContainer). Step arguments
 * are resolved through {@link #resolve(String)}:
 * <ul>
 *   <li>{@code ${orderId}} scenario variables (remembered values, API results)</li>
 *   <li>{@code ${ui.base-url}}, {@code ${env:HOME}}, {@code ${secret:admin-password}} configuration</li>
 *   <li>{@code #{Name.firstName}}, {@code #{Internet.emailAddress}} Datafaker expressions</li>
 *   <li>{@code #{unique:user}} a unique value such as {@code user-1a2b3c4d}</li>
 * </ul>
 */
public class ScenarioContext {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern FAKER = Pattern.compile("#\\{([^}]+)}");

    private final Map<String, Object> variables = new LinkedHashMap<>();

    public void set(String name, Object value) {
        variables.put(name, value);
    }

    public Object get(String name) {
        return variables.get(name);
    }

    public Map<String, Object> variables() {
        return variables;
    }

    public String resolve(String text) {
        if (text == null) {
            return null;
        }
        Matcher variable = VARIABLE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (variable.find()) {
            String name = variable.group(1);
            String replacement = variables.containsKey(name)
                    ? String.valueOf(variables.get(name))
                    : AutomationConfig.get().with(Map.of("__bdd", "${" + name + "}")).get("__bdd");
            variable.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        variable.appendTail(out);

        Matcher faker = FAKER.matcher(out.toString());
        StringBuilder result = new StringBuilder();
        while (faker.find()) {
            String expression = faker.group(1);
            String replacement = expression.startsWith("unique:")
                    ? TestData.unique(expression.substring("unique:".length()))
                    : TestData.faker().expression("#{" + expression + "}");
            faker.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        faker.appendTail(result);
        return result.toString();
    }
}
