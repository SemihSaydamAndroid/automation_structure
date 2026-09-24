package io.github.semihsaydamandroid.automation.core.report;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.qameta.allure.Allure;

/**
 * Reporting facade. Every step and attachment goes to both SLF4J and Allure, so console output
 * in a Kubernetes pod and the Allure report tell the same story. Using this instead of Allure
 * directly keeps test code independent of the report backend.
 */
public final class Reporter {

    private static final Logger LOG = LoggerFactory.getLogger("automation.report");

    private Reporter() {
    }

    /** True when an Allure test case is running (allure-junit5 or AllureKarate is active). */
    public static boolean reporting() {
        return Allure.getLifecycle().getCurrentTestCase().isPresent();
    }

    public static void step(String name) {
        LOG.info("STEP {}", name);
        if (reporting()) {
            Allure.step(name);
        }
    }

    public static void step(String name, Allure.ThrowableRunnableVoid action) {
        LOG.info("STEP {}", name);
        if (reporting()) {
            Allure.step(name, action);
            return;
        }
        try {
            action.run();
        } catch (Throwable t) {
            sneakyThrow(t);
        }
    }

    public static <T> T step(String name, Allure.ThrowableRunnable<T> action) {
        LOG.info("STEP {}", name);
        if (reporting()) {
            return Allure.step(name, action);
        }
        try {
            return action.run();
        } catch (Throwable t) {
            throw sneakyThrow(t);
        }
    }

    public static void parameter(String name, Object value) {
        if (reporting()) {
            Allure.parameter(name, value);
        }
    }

    public static void label(String name, String value) {
        if (reporting()) {
            Allure.label(name, value);
        }
    }

    public static void attachText(String name, String content) {
        attach(name, "text/plain", "txt", content.getBytes(StandardCharsets.UTF_8));
    }

    public static void attachJson(String name, String json) {
        attach(name, "application/json", "json", json.getBytes(StandardCharsets.UTF_8));
    }

    public static void attachHtml(String name, String html) {
        attach(name, "text/html", "html", html.getBytes(StandardCharsets.UTF_8));
    }

    public static void attachMarkdown(String name, String markdown) {
        attach(name, "text/markdown", "md", markdown.getBytes(StandardCharsets.UTF_8));
    }

    public static void attachPng(String name, byte[] png) {
        attach(name, "image/png", "png", png);
    }

    public static void attach(String name, String mimeType, String extension, byte[] content) {
        LOG.debug("Attaching {} ({} bytes, {})", name, content.length, mimeType);
        if (reporting()) {
            Allure.addAttachment(name, mimeType, new ByteArrayInputStream(content), extension);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}
