package io.github.semihsaydamandroid.automation.ui.healing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;

import io.github.semihsaydamandroid.automation.ui.element.Locator;

/**
 * Offline healer: derives candidates from the identifier inside the broken locator (id, name,
 * test id) and from the element description (accessible name, placeholder, visible text).
 */
public final class HeuristicLocatorHealer implements LocatorHealer {

    private static final Pattern QUOTED = Pattern.compile("=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern HASH_ID = Pattern.compile("#([\\w-]+)");
    private static final Pattern SIMPLE_BY = Pattern.compile("^By\\.(id|name|className|linkText|partialLinkText):\\s*(.+)$");
    private static final Set<String> NOISE_WORDS = Set.of(
            "button", "link", "field", "input", "textbox", "checkbox", "dropdown", "icon", "the",
            "butonu", "buton", "alanı", "alani", "linki", "kutusu");

    @Override
    public String name() {
        return "heuristic";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public List<By> candidates(HealingRequest request) {
        Set<By> result = new LinkedHashSet<>();
        identifier(request.locator()).ifPresent(id -> {
            result.add(By.cssSelector("[data-testid='" + id + "']"));
            result.add(By.cssSelector("[data-test='" + id + "']"));
            result.add(By.cssSelector("[data-qa='" + id + "']"));
            result.add(By.id(id));
            result.add(By.name(id));
            result.add(By.cssSelector("[id$='" + id + "']"));
            result.add(By.cssSelector("[aria-label='" + id + "']"));
        });
        for (String text : descriptions(request.locator().description())) {
            String literal = Locator.xpathLiteral(text);
            result.add(By.cssSelector("[aria-label='" + text.replace("'", "\\'") + "' i]"));
            result.add(By.cssSelector("[placeholder='" + text.replace("'", "\\'") + "' i]"));
            result.add(By.xpath("//button[normalize-space(.)=" + literal + "]"));
            result.add(By.xpath("//a[normalize-space(.)=" + literal + "]"));
            result.add(By.xpath("//*[@role='button' and normalize-space(.)=" + literal + "]"));
            result.add(By.xpath("//input[@value=" + literal + "]"));
            result.add(By.xpath("//label[normalize-space(.)=" + literal + "]/following::*[self::input or self::select or self::textarea][1]"));
            result.add(By.xpath("//*[normalize-space(text())=" + literal + "]"));
        }
        result.remove(request.locator().by());
        return new ArrayList<>(result);
    }

    static Optional<String> identifier(Locator locator) {
        String text = locator.by().toString();
        Matcher simple = SIMPLE_BY.matcher(text);
        if (simple.matches()) {
            return Optional.of(simple.group(2).trim());
        }
        Matcher quoted = QUOTED.matcher(text);
        if (quoted.find()) {
            return Optional.of(quoted.group(1));
        }
        Matcher hash = HASH_ID.matcher(text);
        if (hash.find()) {
            return Optional.of(hash.group(1));
        }
        return Optional.empty();
    }

    static List<String> descriptions(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        Set<String> variants = new LinkedHashSet<>();
        variants.add(description.trim());
        String stripped = String.join(" ", Arrays.stream(description.trim().split("\\s+"))
                .filter(word -> !NOISE_WORDS.contains(word.toLowerCase(Locale.ROOT)))
                .toList());
        if (!stripped.isBlank()) {
            variants.add(stripped);
        }
        return new ArrayList<>(variants);
    }
}
