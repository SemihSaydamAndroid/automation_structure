package io.github.semihsaydamandroid.automation.bdd.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.core.config.AutomationConfig;
import io.github.semihsaydamandroid.automation.ui.element.Locator;

/**
 * Optional object repository: {@code *.locators} files below {@code elements/} on the classpath map
 * business names used in feature files to locators.
 *
 * <pre>
 * # src/test/resources/elements/login.locators
 * Kullanıcı adı = testId:username
 * Şifre         = css:input[type=password]
 * Giriş butonu  = id:submit
 * </pre>
 *
 * Strategies: {@code testId, id, name, css, xpath, text, linkText}. Names not listed here are
 * resolved by {@link ByVisibleName}.
 */
public final class ElementRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ElementRegistry.class);
    private static volatile Map<String, Locator> entries;

    private ElementRegistry() {
    }

    public static Optional<Locator> find(String name) {
        return Optional.ofNullable(entries().get(normalize(name)));
    }

    public static Map<String, Locator> entries() {
        Map<String, Locator> result = entries;
        if (result == null) {
            synchronized (ElementRegistry.class) {
                if (entries == null) {
                    entries = Collections.unmodifiableMap(load(AutomationConfig.get().get("bdd.elements-dir", "elements")));
                }
                result = entries;
            }
        }
        return result;
    }

    /** Forces a reload, e.g. after changing configuration in tests. */
    public static synchronized void reset() {
        entries = null;
    }

    static Map<String, Locator> load(String directory) {
        Map<String, Locator> result = new LinkedHashMap<>();
        for (URL file : locatorFiles(directory)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.openStream(), StandardCharsets.UTF_8))) {
                String line;
                int number = 0;
                while ((line = reader.readLine()) != null) {
                    number++;
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) {
                        throw new IllegalArgumentException(file + ":" + number + " must look like 'Name = strategy:value'");
                    }
                    String name = trimmed.substring(0, eq).strip();
                    Locator locator = Locator.of(parse(trimmed.substring(eq + 1).strip()), name);
                    if (result.put(normalize(name), locator) != null) {
                        LOG.warn("Element '{}' is defined more than once; {} wins", name, file);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + file, e);
            }
        }
        LOG.debug("Loaded {} element(s) from classpath:{}", result.size(), directory);
        return result;
    }

    static By parse(String spec) {
        int colon = spec.indexOf(':');
        String strategy = colon > 0 ? spec.substring(0, colon).strip() : "";
        String value = colon > 0 ? spec.substring(colon + 1).strip() : spec;
        return switch (strategy) {
            case "testId" -> By.cssSelector("[data-testid='" + value + "']");
            case "id" -> By.id(value);
            case "name" -> By.name(value);
            case "css" -> By.cssSelector(value);
            case "xpath" -> By.xpath(value);
            case "text" -> By.xpath("//*[normalize-space(.)=" + Locator.xpathLiteral(value) + "]");
            case "linkText" -> By.linkText(value);
            default -> By.cssSelector(spec);
        };
    }

    private static String normalize(String name) {
        return name.strip().toLowerCase(java.util.Locale.forLanguageTag("tr"));
    }

    private static List<URL> locatorFiles(String directory) {
        List<URL> files = new ArrayList<>();
        try {
            Enumeration<URL> roots = Thread.currentThread().getContextClassLoader().getResources(directory);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if ("file".equals(root.getProtocol())) {
                    try (var stream = Files.walk(Path.of(root.toURI()))) {
                        for (Path path : stream.filter(p -> p.toString().endsWith(".locators")).sorted().toList()) {
                            files.add(path.toUri().toURL());
                        }
                    }
                } else if ("jar".equals(root.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) root.openConnection();
                    try (JarFile jar = connection.getJarFile()) {
                        for (JarEntry entry : Collections.list(jar.entries())) {
                            if (entry.getName().startsWith(directory + "/") && entry.getName().endsWith(".locators")) {
                                files.add(java.net.URI.create("jar:" + connection.getJarFileURL() + "!/" + entry.getName()).toURL());
                            }
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Cannot scan classpath:" + directory, e);
        }
        return files;
    }
}
