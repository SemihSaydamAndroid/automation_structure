package io.github.semihsaydamandroid.automation.k8s.load;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the hosts a JMeter plan will hit and checks them against {@code k8s.perf.allowed-hosts}.
 * Distributed load is only started against systems explicitly allowed for this project, so the
 * platform cannot be pointed at third-party services by mistake.
 */
public final class JmxTargets {

    private static final Pattern DOMAIN = Pattern.compile("<stringProp name=\"HTTPSampler\\.domain\">([^<]*)</stringProp>");
    private static final Pattern PATH = Pattern.compile("<stringProp name=\"HTTPSampler\\.path\">([^<]*)</stringProp>");
    private static final Pattern PROPERTY_FN = Pattern.compile("\\$\\{__(?:P|property)\\(([^,)]+)(?:,([^)]*))?\\)}");

    private JmxTargets() {
    }

    public record Result(Set<String> hosts, List<String> unresolved) {
    }

    public static Result scan(String jmx, Map<String, String> properties) {
        Set<String> hosts = new LinkedHashSet<>();
        List<String> unresolved = new ArrayList<>();
        Matcher domains = DOMAIN.matcher(jmx);
        while (domains.find()) {
            add(unescape(domains.group(1)), properties, hosts, unresolved);
        }
        Matcher paths = PATH.matcher(jmx);
        while (paths.find()) {
            String path = resolve(unescape(paths.group(1)), properties);
            if (path.matches("(?i)^https?://.*")) {
                try {
                    add(URI.create(path.replace(" ", "%20")).getHost(), properties, hosts, unresolved);
                } catch (IllegalArgumentException e) {
                    unresolved.add(path);
                }
            }
        }
        return new Result(hosts, unresolved);
    }

    /** Throws when a target is not allowed or cannot be determined. */
    public static void requireAllowed(Result targets, List<String> allowed) {
        if (allowed.isEmpty()) {
            throw new IllegalStateException("Distributed load requires k8s.perf.allowed-hosts (targets found: "
                    + targets.hosts() + "). List the hosts this project is allowed to load test.");
        }
        boolean wildcard = allowed.contains("*");
        if (!targets.unresolved().isEmpty() && !wildcard) {
            throw new IllegalStateException("Cannot determine load targets " + targets.unresolved()
                    + "; pass hosts as JMeter properties (${__P(host)}) so they can be verified");
        }
        List<String> denied = targets.hosts().stream().filter(h -> !matches(h, allowed)).toList();
        if (!denied.isEmpty()) {
            throw new IllegalStateException("Load targets " + denied + " are not in k8s.perf.allowed-hosts " + allowed);
        }
    }

    static boolean matches(String host, List<String> allowed) {
        String h = host.toLowerCase(Locale.ROOT);
        for (String pattern : allowed) {
            String p = pattern.toLowerCase(Locale.ROOT).trim();
            if (p.equals("*") || p.equals(h) || (p.startsWith("*.") && h.endsWith(p.substring(1)))) {
                return true;
            }
        }
        return false;
    }

    private static void add(String rawHost, Map<String, String> properties, Set<String> hosts, List<String> unresolved) {
        if (rawHost == null) {
            return;
        }
        String host = resolve(rawHost, properties).trim();
        if (host.isEmpty()) {
            return; // taken from HTTP Request Defaults, which is scanned as well
        }
        if (host.contains("${")) {
            unresolved.add(host);
        } else {
            hosts.add(host.toLowerCase(Locale.ROOT));
        }
    }

    private static String resolve(String value, Map<String, String> properties) {
        Matcher matcher = PROPERTY_FN.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String replacement = properties.getOrDefault(name, matcher.group(2));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String unescape(String xml) {
        return xml.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }
}
