package io.github.semihsaydamandroid.automation.ai.model;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks personal data and credentials before anything is sent to a model (KVKK / GDPR).
 * Applied to error messages, DOM snapshots and HTTP logs when {@code ai.redact=true}.
 */
public final class Redactor {

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)(authorization|x-api-key|api[-_]?key|cookie|set-cookie)(\"?\\s*[:=]\\s*\"?)[^\"\\n,}]+"), "$1$2<redacted>"),
            new Rule(Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]+"), "Bearer <redacted>"),
            new Rule(Pattern.compile("(?i)(\"?(password|passwd|secret|token|client_secret|access_token|refresh_token)\"?\\s*[:=]\\s*\"?)[^\"\\s&,}]+"), "$1<redacted>"),
            new Rule(Pattern.compile("(?i)(<input[^>]*type=[\"']password[\"'][^>]*value=[\"'])[^\"']*"), "$1<redacted>"),
            new Rule(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email>"),
            new Rule(Pattern.compile("\\bTR\\d{2}(?:\\s?\\d{4}){5}\\s?\\d{2}\\b"), "<iban>"),
            new Rule(Pattern.compile("\\b(?:\\d[ -]?){15,18}\\d\\b"), "<card>"),
            new Rule(Pattern.compile("\\b[1-9]\\d{10}\\b"), "<tckn>"),
            new Rule(Pattern.compile("(?<!\\d)(?:\\+90|0)?\\s?5\\d{2}\\s?\\d{3}\\s?\\d{2}\\s?\\d{2}(?!\\d)"), "<phone>"));

    private Redactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }
}
