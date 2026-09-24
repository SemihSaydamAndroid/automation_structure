package io.github.semihsaydamandroid.automation.core.util;

import java.text.Normalizer;
import java.util.Locale;

/** Name conversions shared by config, secrets and Kubernetes resources. */
public final class Names {

    private Names() {
    }

    /** {@code ui.page-load.timeout} becomes {@code UI_PAGE_LOAD_TIMEOUT}. */
    public static String toEnvStyle(String key) {
        return key.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    /**
     * Converts arbitrary text into an RFC 1123 label usable as a Kubernetes name or label value:
     * lowercase alphanumerics and '-', at most {@code maxLength} characters, starting and ending
     * with an alphanumeric. Turkish characters are transliterated ("Şeyma" becomes "seyma").
     */
    public static String toDnsLabel(String text, int maxLength) {
        String ascii = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replace('ı', 'i')
                .replace('İ', 'I')
                .replaceAll("\\p{M}", "");
        String label = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "");
        if (label.length() > maxLength) {
            label = label.substring(0, maxLength);
        }
        label = label.replaceAll("-+$", "");
        return label.isEmpty() ? "x" : label;
    }

    public static String toDnsLabel(String text) {
        return toDnsLabel(text, 63);
    }
}
