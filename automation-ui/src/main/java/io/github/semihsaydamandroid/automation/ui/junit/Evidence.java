package io.github.semihsaydamandroid.automation.ui.junit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.semihsaydamandroid.automation.core.report.Reporter;
import io.github.semihsaydamandroid.automation.core.spi.FailureAnalysis;
import io.github.semihsaydamandroid.automation.core.spi.FailureAnalyzers;
import io.github.semihsaydamandroid.automation.core.spi.FailureContext;
import io.github.semihsaydamandroid.automation.ui.driver.DriverSession;

/** Captures and reports browser state for a failed test. Never throws. */
public final class Evidence {

    private static final Logger LOG = LoggerFactory.getLogger(Evidence.class);
    private static final int MAX_SOURCE_CHARS = 200_000;

    private Evidence() {
    }

    /** Attaches screenshot, DOM and console log, then returns the failure analysis (if any). */
    public static Optional<FailureAnalysis> captureFailure(String testName, Throwable error, DriverSession session) {
        WebDriver driver = session.driver();
        Map<String, String> artifacts = new LinkedHashMap<>();
        byte[] screenshot = null;
        try {
            screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Reporter.attachPng("Screenshot", screenshot);
        } catch (RuntimeException e) {
            LOG.warn("Screenshot failed: {}", e.toString());
        }
        try {
            artifacts.put("url", driver.getCurrentUrl());
            artifacts.put("title", driver.getTitle());
            String source = driver.getPageSource();
            if (source != null) {
                source = source.length() > MAX_SOURCE_CHARS ? source.substring(0, MAX_SOURCE_CHARS) : source;
                artifacts.put("pageSource", source);
                Reporter.attachHtml("Page source", source);
            }
        } catch (RuntimeException e) {
            LOG.warn("Page state capture failed: {}", e.toString());
        }
        if (!session.consoleLog().isEmpty()) {
            String console = String.join("\n", session.consoleLog());
            artifacts.put("browserConsole", console);
            Reporter.attachText("Browser console", console);
        }
        artifacts.put("session", session.description());
        return FailureAnalyzers.analyzeAndReport(new FailureContext(testName, "ui", error,
                error == null ? null : error.getMessage(), artifacts, screenshot));
    }
}
