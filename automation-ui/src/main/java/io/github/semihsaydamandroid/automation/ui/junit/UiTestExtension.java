package io.github.semihsaydamandroid.automation.ui.junit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.openqa.selenium.WebDriver;

import io.github.semihsaydamandroid.automation.ui.driver.DriverManager;

/**
 * JUnit 5 lifecycle for UI tests: lazily starts the browser when a test asks for a
 * {@link WebDriver} (or calls {@link DriverManager#start()}), captures evidence on failure and
 * always closes the session after the test.
 */
public class UiTestExtension implements ParameterResolver, AfterEachCallback {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == WebDriver.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return DriverManager.start();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        try {
            context.getExecutionException().ifPresent(error -> DriverManager.session()
                    .ifPresent(session -> Evidence.captureFailure(context.getDisplayName(), error, session)));
        } finally {
            DriverManager.quit();
        }
    }
}
