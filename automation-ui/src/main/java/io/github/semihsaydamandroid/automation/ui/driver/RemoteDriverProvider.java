package io.github.semihsaydamandroid.automation.ui.driver;

import java.net.URI;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;

/** Connects to an existing Selenium Grid (or any W3C WebDriver endpoint) at {@code ui.grid-url}. */
public final class RemoteDriverProvider implements DriverProvider {

    @Override
    public ExecutionTarget target() {
        return ExecutionTarget.REMOTE;
    }

    @Override
    public DriverSession create(UiSettings settings, Capabilities capabilities) {
        return DriverSession.of(connect(URI.create(settings.gridUrl()), settings, capabilities),
                settings.browser().name().toLowerCase() + "@" + settings.gridUrl());
    }

    /** Shared with other providers that end up talking to a WebDriver endpoint. */
    public static WebDriver connect(URI endpoint, UiSettings settings, Capabilities capabilities) {
        ClientConfig config = ClientConfig.defaultConfig()
                .readTimeout(settings.remoteReadTimeout());
        return RemoteWebDriver.builder()
                .oneOf(capabilities)
                .address(endpoint)
                .config(config)
                .build();
    }
}
