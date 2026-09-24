package io.github.semihsaydamandroid.automation.core.config;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human friendly durations used in configuration files.
 * <p>
 * Accepted forms: {@code 500ms}, {@code 10s}, {@code 2m}, {@code 1h}, {@code 1d}, ISO-8601
 * ({@code PT30S}) and bare numbers, which are interpreted as seconds.
 */
public final class Durations {

    private static final Pattern SIMPLE = Pattern.compile("(\\d+)\\s*(ms|s|m|h|d)?");

    private Durations() {
    }

    public static Duration parse(String text) {
        if (text == null || text.isBlank()) {
            throw new ConfigException("Duration value is empty");
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("p")) {
            return Duration.parse(value.toUpperCase(Locale.ROOT));
        }
        Matcher matcher = SIMPLE.matcher(value);
        if (!matcher.matches()) {
            throw new ConfigException("Invalid duration '" + text + "'. Use e.g. 500ms, 10s, 2m, 1h or PT30S");
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2) == null ? "s" : matcher.group(2);
        return switch (unit) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new ConfigException("Unsupported duration unit in '" + text + "'");
        };
    }
}
