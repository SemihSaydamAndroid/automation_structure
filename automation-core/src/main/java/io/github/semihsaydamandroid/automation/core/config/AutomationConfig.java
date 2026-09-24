package io.github.semihsaydamandroid.automation.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.semihsaydamandroid.automation.core.context.ExecutionContext;
import io.github.semihsaydamandroid.automation.core.context.ExecutionProfile;
import io.github.semihsaydamandroid.automation.core.secrets.Secrets;
import io.github.semihsaydamandroid.automation.core.util.Names;

/**
 * Single source of configuration for UI, API, performance, Kubernetes and AI modules.
 * <p>
 * Layers, lowest precedence first:
 * <ol>
 *   <li>{@code META-INF/automation-defaults.properties} shipped inside every framework jar</li>
 *   <li>{@code META-INF/automation-defaults-<profile>.properties} framework defaults per profile</li>
 *   <li>{@code automation.properties} on the client's classpath</li>
 *   <li>{@code automation-<profile>.properties} where profile is {@code local} or {@code ci}</li>
 *   <li>{@code automation-<env>.properties} where env comes from {@code -Denv} / {@code AUTOMATION_ENV}</li>
 *   <li>an external file from {@code -Dautomation.config.file} / {@code AUTOMATION_CONFIG_FILE}
 *       (for example a mounted Kubernetes ConfigMap)</li>
 *   <li>environment variables: {@code AUTOMATION_UI_BROWSER} overrides {@code ui.browser}</li>
 *   <li>system properties: {@code -Dui.browser=firefox}</li>
 * </ol>
 * Values may reference {@code ${other.key}}, {@code ${env:NAME}} and {@code ${secret:name}},
 * each optionally with a default: {@code ${api.url:-http://localhost:8080}}.
 */
public final class AutomationConfig {

    public static final String ENV_PREFIX = "AUTOMATION_";
    public static final String DEFAULTS_RESOURCE = "META-INF/automation-defaults.properties";

    private static final Set<String> NAMESPACES =
            Set.of("automation", "ui", "api", "perf", "k8s", "ai", "report", "data", "env");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final int MAX_DEPTH = 10;

    private static volatile AutomationConfig current;

    private final Map<String, String> raw;
    private final List<String> sources;
    private final Map<String, String> env;
    private final Secrets secrets;
    private final ExecutionContext context;

    private AutomationConfig(Map<String, String> raw, List<String> sources, Map<String, String> env, Secrets secrets) {
        this.raw = Collections.unmodifiableMap(new TreeMap<>(raw));
        this.sources = List.copyOf(sources);
        this.env = env;
        this.secrets = secrets;
        this.context = new ExecutionContext(
                raw.get("automation.env"),
                ExecutionProfile.parse(raw.get("automation.profile")),
                raw.get("automation.run-id"),
                raw.get("automation.owner"),
                env.get("KUBERNETES_SERVICE_HOST") != null);
    }

    /** Process-wide configuration, loaded on first access. */
    public static AutomationConfig get() {
        AutomationConfig config = current;
        if (config == null) {
            synchronized (AutomationConfig.class) {
                config = current;
                if (config == null) {
                    config = load();
                    current = config;
                }
            }
        }
        return config;
    }

    /** Replaces the process-wide configuration, mainly for tests and embedding. */
    public static synchronized void set(AutomationConfig config) {
        current = config;
    }

    /** Forces a reload on the next {@link #get()}. */
    public static synchronized void reset() {
        current = null;
    }

    public static AutomationConfig load() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return load(System.getenv(), System.getProperties(),
                loader != null ? loader : AutomationConfig.class.getClassLoader(), Secrets.defaults());
    }

    /** Creates a configuration from explicit values only; no files, environment or system properties. */
    public static AutomationConfig of(Map<String, String> values) {
        Map<String, String> merged = new HashMap<>(values);
        merged.putIfAbsent("automation.profile", ExecutionProfile.LOCAL.id());
        merged.putIfAbsent("automation.env", "default");
        merged.putIfAbsent("automation.run-id", "test-run");
        merged.putIfAbsent("automation.owner", "test");
        return new AutomationConfig(merged, List.of("explicit"), Map.of(), new Secrets(List.of()));
    }

    static AutomationConfig load(Map<String, String> env, Properties systemProperties, ClassLoader loader, Secrets secrets) {
        Map<String, String> merged = new HashMap<>();
        List<String> sources = new ArrayList<>();

        // The client's base file may pin profile/env, so peek at it before layering.
        Map<String, String> clientBase = new HashMap<>();
        List<String> clientBaseSources = new ArrayList<>();
        loadOne(loader, "automation.properties", clientBase, clientBaseSources);

        loadAll(loader, DEFAULTS_RESOURCE, merged, sources);

        ExecutionProfile profile = Optional.ofNullable(firstNonBlank(
                        systemProperties.getProperty("automation.profile"),
                        env.get(ENV_PREFIX + "PROFILE"),
                        clientBase.get("automation.profile"),
                        merged.get("automation.profile")))
                .map(ExecutionProfile::parse)
                .orElseGet(() -> ExecutionProfile.detect(env));
        String envName = Optional.ofNullable(firstNonBlank(
                        systemProperties.getProperty("automation.env"),
                        systemProperties.getProperty("env"),
                        env.get(ENV_PREFIX + "ENV"),
                        clientBase.get("automation.env"),
                        merged.get("automation.env")))
                .orElse("default");

        // Framework defaults per profile, e.g. headless browsers in CI.
        loadAll(loader, "META-INF/automation-defaults-" + profile.id() + ".properties", merged, sources);
        merged.putAll(clientBase);
        sources.addAll(clientBaseSources);
        loadOne(loader, "automation-" + profile.id() + ".properties", merged, sources);
        loadOne(loader, "automation-" + envName + ".properties", merged, sources);

        String externalFile = firstNonBlank(
                systemProperties.getProperty("automation.config.file"), env.get(ENV_PREFIX + "CONFIG_FILE"));
        if (externalFile != null) {
            loadFile(Path.of(externalFile), merged, sources);
        }

        applyEnvironment(env, merged, sources);
        applySystemProperties(systemProperties, merged, sources);

        merged.put("automation.profile", profile.id());
        merged.put("automation.env", envName);
        merged.computeIfAbsent("automation.run-id", k -> detectRunId(env));
        merged.computeIfAbsent("automation.owner", k -> profile == ExecutionProfile.CI
                ? "ci"
                : Names.toDnsLabel(systemProperties.getProperty("user.name", "local"), 30));

        return new AutomationConfig(merged, sources, env, secrets);
    }

    // ---------------------------------------------------------------- accessors

    public ExecutionContext context() {
        return context;
    }

    public List<String> sources() {
        return sources;
    }

    public boolean has(String key) {
        return raw.containsKey(key);
    }

    public Optional<String> find(String key) {
        String value = raw.get(key);
        return value == null ? Optional.empty() : Optional.of(resolve(value, 0));
    }

    public String get(String key) {
        return find(key).orElseThrow(() -> new ConfigException(
                "Missing configuration '" + key + "'. Set it in automation.properties, as -D" + key
                        + " or as env " + ENV_PREFIX + Names.toEnvStyle(key)));
    }

    public String get(String key, String defaultValue) {
        return find(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return find(key).map(v -> convert(key, v, Integer::parseInt)).orElse(defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return find(key).map(v -> convert(key, v, Long::parseLong)).orElse(defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return find(key).map(v -> convert(key, v, Double::parseDouble)).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return find(key).map(v -> convert(key, v, AutomationConfig::parseBoolean)).orElse(defaultValue);
    }

    public Duration getDuration(String key, Duration defaultValue) {
        return find(key).map(v -> convert(key, v, Durations::parse)).orElse(defaultValue);
    }

    public <E extends Enum<E>> E getEnum(String key, Class<E> type, E defaultValue) {
        return find(key)
                .map(v -> convert(key, v, s -> Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT).replace('-', '_'))))
                .orElse(defaultValue);
    }

    public List<String> getList(String key) {
        return find(key)
                .map(v -> Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())
                .orElse(List.of());
    }

    /**
     * Resolved values below a prefix with the prefix removed:
     * {@code section("ui.capabilities")} maps {@code ui.capabilities.acceptInsecureCerts} to
     * {@code acceptInsecureCerts}.
     */
    public Map<String, String> section(String prefix) {
        String dotted = prefix.endsWith(".") ? prefix : prefix + ".";
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key.startsWith(dotted)) {
                result.put(key.substring(dotted.length()), resolve(value, 0));
            }
        });
        return result;
    }

    /** Unresolved view of every key, useful for forwarding configuration to a remote pod. */
    public Map<String, String> rawValues() {
        return raw;
    }

    /** New configuration with the given values layered on top. */
    public AutomationConfig with(Map<String, String> overrides) {
        Map<String, String> merged = new HashMap<>(raw);
        merged.putAll(overrides);
        List<String> newSources = new ArrayList<>(sources);
        newSources.add("overrides");
        return new AutomationConfig(merged, newSources, env, secrets);
    }

    // ---------------------------------------------------------------- resolution

    private String resolve(String value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new ConfigException("Placeholder nesting too deep (cycle?) while resolving '" + value + "'");
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        do {
            String replacement = resolveExpression(matcher.group(1).trim(), depth);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        } while (matcher.find());
        matcher.appendTail(out);
        return resolve(out.toString(), depth + 1);
    }

    private String resolveExpression(String expression, int depth) {
        String name = expression;
        String fallback = null;
        int defaultIndex = expression.indexOf(":-");
        if (defaultIndex >= 0) {
            name = expression.substring(0, defaultIndex).trim();
            fallback = expression.substring(defaultIndex + 2);
        }
        Optional<String> value;
        if (name.startsWith("env:")) {
            value = Optional.ofNullable(env.get(name.substring(4)));
        } else if (name.startsWith("secret:")) {
            value = secrets.find(name.substring(7));
        } else {
            value = Optional.ofNullable(raw.get(name)).map(v -> resolve(v, depth + 1));
        }
        if (value.isPresent()) {
            return value.get();
        }
        if (fallback != null) {
            return fallback;
        }
        throw new ConfigException("Cannot resolve placeholder ${" + expression + "}");
    }

    // ---------------------------------------------------------------- loading helpers

    private static void loadAll(ClassLoader loader, String resource, Map<String, String> target, List<String> sources) {
        try {
            Enumeration<URL> urls = loader.getResources(resource);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream()) {
                    putAll(in, target);
                }
                sources.add(url.toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    private static void loadOne(ClassLoader loader, String resource, Map<String, String> target, List<String> sources) {
        URL url = loader.getResource(resource);
        if (url == null) {
            return;
        }
        try (InputStream in = url.openStream()) {
            putAll(in, target);
            sources.add("classpath:" + resource);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    private static void loadFile(Path file, Map<String, String> target, List<String> sources) {
        if (!Files.isRegularFile(file)) {
            throw new ConfigException("Configuration file not found: " + file.toAbsolutePath());
        }
        try (InputStream in = Files.newInputStream(file)) {
            putAll(in, target);
            sources.add("file:" + file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static void putAll(InputStream in, Map<String, String> target) throws IOException {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String name : properties.stringPropertyNames()) {
            target.put(name.trim(), properties.getProperty(name).trim());
        }
    }

    private static void applyEnvironment(Map<String, String> env, Map<String, String> target, List<String> sources) {
        Map<String, String> byEnvName = new HashMap<>();
        for (String key : target.keySet()) {
            byEnvName.put(ENV_PREFIX + Names.toEnvStyle(key), key);
        }
        // Short aliases for run identity; the Kubernetes remote runner forwards these to pods.
        byEnvName.put(ENV_PREFIX + "RUN_ID", "automation.run-id");
        byEnvName.put(ENV_PREFIX + "OWNER", "automation.owner");
        boolean applied = false;
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(ENV_PREFIX) || name.startsWith(ENV_PREFIX + "SECRET_")) {
                continue;
            }
            String key = byEnvName.getOrDefault(name,
                    name.substring(ENV_PREFIX.length()).toLowerCase(Locale.ROOT).replace('_', '.'));
            target.put(key, entry.getValue());
            applied = true;
        }
        if (applied) {
            sources.add("environment");
        }
    }

    private static void applySystemProperties(Properties system, Map<String, String> target, List<String> sources) {
        boolean applied = false;
        for (String name : system.stringPropertyNames()) {
            int dot = name.indexOf('.');
            String namespace = dot > 0 ? name.substring(0, dot) : name;
            if (target.containsKey(name) || (dot > 0 && NAMESPACES.contains(namespace))) {
                target.put(name, system.getProperty(name));
                applied = true;
            }
        }
        if (applied) {
            sources.add("system-properties");
        }
    }

    private static String detectRunId(Map<String, String> env) {
        String ci = firstNonBlank(env.get("GITHUB_RUN_ID"), env.get("BUILD_TAG"), env.get("CI_PIPELINE_ID"),
                env.get("BUILD_BUILDID"), env.get("TESTKUBE_EXECUTION_ID"));
        if (ci != null) {
            return Names.toDnsLabel(ci, 40);
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean parseBoolean(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new IllegalArgumentException("not a boolean: " + value);
        };
    }

    private static <T> T convert(String key, String value, Function<String, T> converter) {
        try {
            return converter.apply(value.trim());
        } catch (RuntimeException e) {
            throw new ConfigException("Invalid value '" + value + "' for configuration '" + key + "'", e);
        }
    }

    @Override
    public String toString() {
        return "AutomationConfig" + context + " from " + sources;
    }
}
