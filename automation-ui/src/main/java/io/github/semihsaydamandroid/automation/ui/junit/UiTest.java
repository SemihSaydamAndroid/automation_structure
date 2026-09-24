package io.github.semihsaydamandroid.automation.ui.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a UI test class or method. A browser session is started per test (on the configured
 * target: local, grid or Kubernetes pod), injected as a {@code WebDriver} parameter when
 * requested, and on failure the screenshot, DOM, console log and a failure analysis are attached
 * to the report before the session is closed.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("ui")
@ExtendWith(UiTestExtension.class)
public @interface UiTest {
}
