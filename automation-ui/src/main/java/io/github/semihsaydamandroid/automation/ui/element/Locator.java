package io.github.semihsaydamandroid.automation.ui.element;

import org.openqa.selenium.By;

/**
 * A {@link By} plus a human description of what the element is. The description drives readable
 * logs and reports, and gives the self-healing layer (heuristic and AI) the intent it needs to
 * find the element again after the DOM changed.
 */
public record Locator(By by, String description) {

    public static Locator of(By by, String description) {
        return new Locator(by, description);
    }

    public static Locator css(String selector, String description) {
        return new Locator(By.cssSelector(selector), description);
    }

    public static Locator xpath(String expression, String description) {
        return new Locator(By.xpath(expression), description);
    }

    public static Locator id(String id, String description) {
        return new Locator(By.id(id), description);
    }

    public static Locator name(String name, String description) {
        return new Locator(By.name(name), description);
    }

    /** Preferred: stable {@code data-testid} attributes agreed with developers. */
    public static Locator testId(String testId, String description) {
        return new Locator(By.cssSelector("[data-testid='" + testId + "']"), description);
    }

    /** Element whose normalized text equals {@code text}. */
    public static Locator text(String text, String description) {
        return new Locator(By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + "]"), description);
    }

    public static String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        return "concat('" + value.replace("'", "',\"'\",'") + "')";
    }

    @Override
    public String toString() {
        return "'" + description + "' [" + by + "]";
    }
}
