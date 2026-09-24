package io.github.semihsaydamandroid.automation.bdd.support;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.InvalidSelectorException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import io.github.semihsaydamandroid.automation.ui.healing.HeuristicLocatorHealer;

/**
 * Finds an element by the name a human would use: its test id, id or name attribute, accessible
 * name, placeholder, label or visible text. This lets feature files say {@code When I click
 * "Sign in"} without a locator; visible matches win over hidden ones.
 */
public final class ByVisibleName extends By {

    private final String name;
    private final List<By> candidates;

    public ByVisibleName(String name) {
        this.name = name;
        this.candidates = HeuristicLocatorHealer.candidatesForName(name);
    }

    @Override
    public List<WebElement> findElements(SearchContext context) {
        List<WebElement> hiddenMatches = List.of();
        for (By candidate : candidates) {
            try {
                List<WebElement> found = context.findElements(candidate);
                List<WebElement> visible = found.stream().filter(ByVisibleName::displayed).toList();
                if (!visible.isEmpty()) {
                    return visible;
                }
                if (hiddenMatches.isEmpty() && !found.isEmpty()) {
                    hiddenMatches = found;
                }
            } catch (InvalidSelectorException | StaleElementReferenceException e) {
                // names with quotes produce invalid CSS for some candidates; try the next one
            }
        }
        return hiddenMatches;
    }

    private static boolean displayed(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "By.visibleName: " + name;
    }
}
