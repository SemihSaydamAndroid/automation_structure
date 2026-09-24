package io.github.semihsaydamandroid.automation.ui.driver;

import org.openqa.selenium.Capabilities;

/**
 * SPI that creates browser sessions for one {@link ExecutionTarget}.
 * <p>
 * LOCAL and REMOTE are built in. Other modules contribute providers through
 * {@code META-INF/services/io.github.semihsaydamandroid.automation.ui.driver.DriverProvider};
 * {@code automation-k8s} registers the KUBERNETES provider this way. A client can register its own
 * provider (a cloud grid, Selenoid, Moon) the same way.
 */
public interface DriverProvider {

    ExecutionTarget target();

    DriverSession create(UiSettings settings, Capabilities capabilities);
}
