package io.github.semihsaydamandroid.automation.ai.model;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shrinks an HTML page to what matters for locating elements: no scripts, styles, SVG or
 * comments, and only identifying attributes. Typically cuts a page by 80-95%, which keeps prompts
 * fast and cheap on small local models.
 */
public final class DomCompactor {

    private static final Pattern DROP_BLOCKS = Pattern.compile(
            "(?is)<(script|style|svg|noscript|template|iframe)\\b.*?</\\1>|<!--.*?-->|<(link|meta|base)\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("(?s)<([a-zA-Z][a-zA-Z0-9-]*)(\\s[^>]*?)?(/?)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"[^\"]*\"|'[^']*')");
    private static final Set<String> KEEP = Set.of("id", "name", "type", "role", "placeholder", "title", "alt",
            "href", "value", "for", "class", "aria-label", "aria-labelledby", "aria-describedby", "label");

    private DomCompactor() {
    }

    public static String compact(String html) {
        if (html == null) {
            return "";
        }
        String text = DROP_BLOCKS.matcher(html).replaceAll("");
        Matcher tag = TAG.matcher(text);
        StringBuilder out = new StringBuilder();
        while (tag.find()) {
            String attributes = tag.group(2) == null ? "" : keptAttributes(tag.group(2));
            tag.appendReplacement(out, Matcher.quoteReplacement("<" + tag.group(1) + attributes + tag.group(3) + ">"));
        }
        tag.appendTail(out);
        return out.toString().replaceAll("\\s{2,}", " ").replaceAll(">\\s+<", "><").trim();
    }

    private static String keptAttributes(String raw) {
        StringBuilder out = new StringBuilder();
        Matcher attribute = ATTRIBUTE.matcher(raw);
        while (attribute.find()) {
            String name = attribute.group(1).toLowerCase();
            if (KEEP.contains(name) || name.startsWith("data-test") || name.startsWith("data-qa") || name.startsWith("data-cy")) {
                String value = attribute.group(2);
                if (name.equals("class") && value.length() > 60) {
                    value = value.substring(0, 59) + value.charAt(0);
                }
                out.append(' ').append(name).append('=').append(value);
            }
        }
        return out.toString();
    }
}
